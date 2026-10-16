# Issue #2 性能与架构优化（PR #4 追加）

在 PR #4 的「历史文档应用内预览、继续扫描与原记录自动保存」基础上，针对**文档保存过重、首次预览整批等待、PDF 强制生成**三项问题做架构改造。原则：快门优先、预览优先、按页处理、增量保存、PDF 按需生成。保留 PR #4 的历史续扫、多页编辑、高清扫描、页面恢复与数据保护能力。

## 一、新旧保存路径对比

| 维度 | 旧实现（`DocumentStore.writeVersion`） | 新实现（独立页面资产 + 清单提交） |
| --- | --- | --- |
| 单次保存动作 | 生成完整新版本目录：全部页面原图 + 处理图 + 缩略图 + 文档 JSON + 完整 PDF + 封面 | 只写「新增或真正变化」的页面文件，然后原子替换 `document.json` |
| 改一页的成本 | 随总页数线性增长（复制 N 页图片） | 与该页相关：1 个原图或 1 个处理缓存 + 1 次清单写入 |
| PDF | 每次保存都 `writePdfToStream()` 全量生成 | 保存不生成 PDF；仅分享 / 打开 / 打印 / 导出时按指纹生成或复用 |
| 提交点 | 目录切换 + 索引指针 | 清单原子替换（唯一提交点），页面文件先落盘 |
| 失败回退 | 依赖「整份版本已复制」 | 旧清单仍指向旧资产；新文档目录整体回退 |
| 清理 | 保存后清理未引用版本 | 提交后清理：未引用页面目录、旧内容版本原图、旧缓存键、未提交页面的孤儿缓存 |

新增文件：`history/DocumentManifest.kt`（纯数据模型 + 增量规划器，可 JVM 单测）、`scan/PageProcessor.kt`、`scan/PageProcessingQueue.kt`。

## 二、磁盘结构与关键数据结构

```
filesDir/history/
├── records.json（AtomicFile，历史索引）
├── documents/{documentId}/
│   ├── document.json            # 唯一提交点：清单（schemaVersion 2）
│   ├── thumbnail.jpg            # 封面缓存（按首页签名失效）
│   ├── pages/{pageId}/{contentRevision}.png   # 不可变原图，一个内容版本一个文件
│   └── cache/                   # 全部可重建缓存
│       ├── pages/{pageId}/{cacheKey}.png      # 处理图
│       ├── pages/{pageId}/{cacheKey}.jpg      # 预览图
│       └── document.pdf (+ document.pdf.meta) # 按需 PDF 与指纹
```

- `ManifestPageEntry`：`id / contentRevision / cacheKey / rotationDegrees / correctionMode / imageStyle / quad`。
- `cacheKey = sha256(contentRevision | correctionMode | imageStyle | quad)`。**旋转不参与缓存键**（处理图本身不旋转，旋转只是展示参数）。
- 原图按 `contentRevision` 命名，天然不可变：替换页面 = 新 revision 文件 + 旧文件提交后清理。
- schema 1（PR #4 版本目录）仍可读：`loadPages()` 兼容旧路径，PDF 生成 / 清单收敛时把旧原图与处理图迁移到新位置。不做 PR #4 之前更早的历史兼容。

## 三、增量保存规则

`planPageSave(old, new)` 是纯函数（无 Android 依赖），决定每页写什么：

| 场景 | 写原图 | 写处理缓存 | 清理 |
| --- | --- | --- | --- |
| 新拍摄 / 新增页 | ✔ | 有结果才写 | — |
| 页面未处理完就保存 | ✔ | ✘（下次补写） | — |
| 后台处理完成 | ✘ | ✔ | — |
| 旋转 / 排序 | ✘ | ✘ | — |
| 替换页面（同 ID 新内容） | ✔（新 revision） | ✔（新 cacheKey） | 旧 revision + 旧 cacheKey |
| 调整裁剪 / 矫正模式 | ✘ | ✔（新 cacheKey） | 旧 cacheKey |
| 无修改退出 | ✘ | ✘ | — |
| 后台已写好缓存后首次提交 | ✔ | ✘（不重复编码） | — |

「写处理缓存」以目标文件是否已存在为准（`!processedCacheExists`），因此后台队列直接写进文档缓存目录的结果不会被再编码一次。

## 四、可靠性

1. 页面文件先写 tmp 再 `renameTo`，清单用 tmp + 原子替换；只有页面资产全部就位才提交清单。
2. 保存串行化（`storageLock` + 单个长生命周期保存消费者），快照在协程内捕获，新旧状态不交错。
3. 保存失败保留最后一次有效清单；新建文档失败整体删除目录，已有文档旧清单仍指向旧资产。
4. **索引与清单的一致性**：清单一律先于索引落盘，索引（`records.json`，AtomicFile）写入失败时 `HistoryRepository` 会用保存前读取的清单字节做补偿回滚（`DocumentStore.restoreManifest`）。这样两个提交点要么同时是旧状态、要么同时是新状态，不会出现「页面已旋转但历史仍显示未保存」的撕裂状态；回滚产生的孤立页面资产在下一次成功保存的清理阶段回收。
5. 清理永远发生在提交之后：`cleanupObsoleteAssets()` 只删未被新清单引用的页面目录、非当前 revision 的原图、非当前 cacheKey 的缓存，以及未提交页面的孤儿缓存；失败只告警不影响保存结果。
6. 删除记录时 `documents/{id}` 整体删除。

## 五、新旧预览路径对比

| 维度 | 旧实现 | 新实现 |
| --- | --- | --- |
| 进入预览 | `openPreviewAt()` 先 `ensurePagesProcessed()` 串行处理全部页面，全局遮罩 + `isProcessingPreview` | 立即切到 `ScanStage.Preview`，未处理页面后台逐页补处理并自动更新 |
| 阻塞 | 全部页面处理完成前阻塞交互 | 无整页遮罩；保存改为静默的 `isSavingDocument` 标记 |
| 单页状态 | 无 | `PageProcessingPhase`：`PENDING / PROCESSING / READY / FAILED`，失败页可点击重试 |
| 未处理页显示 | 不可用 | 快速预览（原图降采样）占位 + 明确状态提示，避免误认为最终成品 |
| 历史文档重开 | 全部重新处理 | 已有处理缓存直接复用；缺失缓存的页后台续处理 |
| 导出保护 | 依赖全局处理完成 | `runWhenPagesReady()`：当前页优先，等所有页 `READY` 再导出；有失败页则提示重试，不导出缺页 PDF |

## 六、逐页处理队列

`PageProcessingQueue`（默认并发 2，主线程 API，重活在 `Dispatchers.Default`）：

- **优先级**：`promote(pageId)`（当前查看页）＞ 最近提交（刚拍摄 / 刚修改）＞ 其余；用户翻页即提升该页优先级，不再从第 1 页顺序处理到最后一页。
- **版本绑定**：任务绑定页面 ID，执行时重新读取最新页面快照；结果回调 `shouldApplyProcessedPage(currentRevision, currentProcessed, resultRevision)` 校验，页面被替换 / 删除 / 已就绪时旧结果直接丢弃，不会串图或回退。
- **渐进更新**：`processDocumentPage()` 在透视矫正完成后先回调中间结果（裁剪预览上屏），再做图像增强并回调最终结果；中间结果同样按内容版本校验。
- **结果落点**：已保存文档的页面直接写入 `cache/pages/{pageId}/{cacheKey}.png`（顺带写预览 jpg）；未保存的新页写应用缓存临时目录，保存提交后清理。
- **生命周期**：`finishDocument()` / 切换任务时 `reset()` 丢弃等待任务。

`PageProcessor.processDocumentPage()` 被扫描流程与「PDF 按需生成前的缺页补处理」共用，避免两条链路重复实现：边缘检测 → 透视矫正 → 增强 → 编码落盘（tmp + 原子替换）→ 预览图；位图按依赖顺序回收，且回收时避开被 UI 持有的预览位图。

## 七、PDF 按需生成与缓存

- `HistoryRepository.saveDocumentRecord()` 不再调用 `writePdfToStream()`；保存后 `DocumentStore.save()` 直接 `invalidatePdfCache()`。
- `ensureDocumentPdf(recordId)`：读取记录指纹，若 `document.pdf` 与 `.meta` 均存在且 `.meta == documentFingerprint` 则直接复用；否则补齐未处理页面（复用已有缓存 / 迁移 schema 1 处理图 / 现场补处理），原子生成 PDF 与 `.meta`，并收敛清单。
- 触发点只有：历史分享、历史打开（用外部应用查看）、打印、导出分享。历史列表分享入口不再假设 PDF 恒存在。
- 生成在后台执行（`Dispatchers.IO`），失败只提示，不影响文档本身。

## 八、缓存与内存

- 原图只在内容版本变化时写一次；重开文档不重新编码原图。
- 处理图 / 预览图 / PDF 全部为可重建缓存，按 cacheKey 失效重建；清理只针对未引用资产，不动原图。
- 常驻预览图统一受 `PREVIEW_MAX_SIDE = 640` 约束：持久化预览缓存与「缓存缺失时降级解码原图」同口径，历史列表与预览页的内存峰值可控。
- 预览页只给当前可见页加载较高清晰度（2048）图像，其余页用小图；Bitmap 由 `produceState` 按页 key 生成并随组合退出释放。
- 后台并发固定为 2，连续拍摄时不被全速高清处理拖慢；拍摄后新页立即入列，处理与下一次快门重叠。
- 拍摄链路保持只做解码 + 旋转 + 缩略图，透视矫正与增强全部下放到后台队列。

## 九、验证结果

**构建与静态检查**

| 项目 | 命令 | 结果 |
| --- | --- | --- |
| Kotlin 编译 | `:app:compileDebugKotlin` | BUILD SUCCESSFUL |
| JVM 单元测试 | `:app:testDebugUnitTest` | 27 项全部通过 |
| 主 APK / 测试 APK | `:app:assembleDebug` `:app:assembleDebugAndroidTest` | BUILD SUCCESSFUL |
| Lint | `:app:lintDebug` | 0 error / 64 warning / 5 hint；`DocumentManifest.kt`、`PageProcessor.kt`、`PageProcessingQueue.kt`、`DocumentStore.kt` **零告警**，告警均为既有文件的 `UseKtx` / `DefaultLocale` 风格提示 |

JVM 单测分布：`DocumentManifestTest` 11、`PageProcessingQueueTest` 10、`BitmapUtilsTest` 5、`ExampleUnitTest` 1。

**真机测试**（OnePlus PKR110 / Android 16）

已执行的非相机用例 **8 项全部通过**：

| 测试类 | 用例 | 结果 |
| --- | --- | --- |
| `HistoryDocumentPersistenceTest` | `createReopenAppendEditAndDeleteSameDocument` | ✔ 保存不生成 PDF、追加不重写未变化原图、旋转/排序不重建图片、PDF 按需生成 4 页、无修改复用 PDF 缓存且字节一致 |
| 同上 | `failedUpdateKeepsCommittedFilesAndIndex` | ✔ 资产写入失败与索引写入失败两种情况，已提交清单与索引均不变（新增清单回滚断言） |
| 同上 | `unprocessedPagePersistsSourceAndResumesProcessing` | ✔ 未处理页只落原图，需要 PDF 时再补处理 |
| `HistoryQualityAndReorderE2eTest` | `sixPageDragReachesLastCellAndSettlesAfterRelease` | ✔ |
| 同上 | `reopenedPageShowsFineDetailAndRetainsFullSizeExport` | ✔ 重开预览 `height ≤ 640`，导出仍为 3000×4000 原尺寸 |
| `HistoryRescanE2eTest` | `recentAndAllDocumentsRestorePreviewAndRotationWithoutNoOpWrites` | ✔ |
| 同上 | `reorderCropCancelAndDeleteAllPersistThroughExistingControls` | ✔ |
| 同上 | `failedAutoSaveKeepsOriginalAndRetryUpdatesSameRecord` | ✔ 失败后清单未推进，重试写入同一条记录 |

**本轮修复的三个真机问题**

1. 后台队列写页面缓存时目标目录未创建 → `PageProcessor` 写 tmp 前先 `mkdirs()`。
2. 持久化预览图沿用内存里的 1280 上限 → 落盘预览缓存与降级解码统一降到 `PREVIEW_MAX_SIDE = 640`。
3. **索引写入失败时清单已先提交**（页面旋转 `0.0 → 90.0`，长度 +1、封面签名随之改变）→ `HistoryRepository` 在索引提交失败时用保存前读取的清单字节回滚，两个提交点恢复一致；`HistoryDocumentPersistenceTest` 相应补上清单回滚断言。

**仍需真实相机的用例**（9 项，需把镜头对准文档后跑 `tools/run-history-rescan-e2e.ps1` 的 17 项完整脚本）：`HistoryRescanE2eTest#addTwoCameraPagesToExistingThreeAndReopenSameFivePageDocument`、`HistoryRescanE2eTest#missingTemporaryPagesAreReprocessedOnRetryWithoutLosingOriginal`、`HistoryProcessRestartE2eTest` 3 项、`SmartManualCaptureE2eTest#recognizedManualDocumentWaitsForShutterAndCollectsFivePages`，以及三个冷启动阶段。

## 十、回归清单

自动化（JVM 单测，随 `testDebugUnitTest` 执行）：

- `DocumentManifestTest`（11 项）：未变化页零写入、新增页、未处理页只落原图、旋转不重建图片、替换页资源回收、后台处理完成只补缓存、缓存缺失不写、后台已写缓存不重复编码、缓存键忽略旋转但跟踪四角/模式/风格、封面签名。
- `PageProcessingQueueTest`（10 项）：任务去重、当前页优先、多次提升以最近一次为准、未提升页按最近提交优先、未知/已完成页的提升被忽略、`reset()` 丢弃等待任务，以及结果版本绑定（页面被替换 / 删除 / 已就绪时旧结果一律丢弃）。
- 既有 `BitmapUtilsTest`（5 项）、`ExampleUnitTest`（1 项）保持通过。

需设备/人工验证（真机）：

- 连续拍摄时快门响应与后台处理不互相阻塞。
- 拍摄多页后点预览立即进入，未处理页可见且逐页自动更新。
- 20 页以上长文档的浏览、追加、保存与内存占用。
- 首次分享 / 未修改再分享 / 修改后再导出的 PDF 正确性。
- 保存中断（断电/杀进程）后重开继续处理与保存。
