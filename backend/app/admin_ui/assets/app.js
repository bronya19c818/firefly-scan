const tokenKey = "juziscan_admin_token";
const sidebarCollapsedKey = "juziscan_admin_sidebar_collapsed";

// View panels and login references
const loginView = document.getElementById("login-view");
const dashboardView = document.getElementById("dashboard-view");
const loginForm = document.getElementById("login-form");
const loginError = document.getElementById("login-error");

// Sidebar & navigation elements
const currentAdminName = document.getElementById("current-admin-name");
const currentAdminEmail = document.getElementById("current-admin-email");
const adminAvatar = document.getElementById("admin-avatar");

// Table and grid elements
const statsContainer = document.getElementById("stats-container");
const usersTable = document.getElementById("users-table");
const ordersTable = document.getElementById("orders-table");
const feedbacksTable = document.getElementById("feedbacks-table");

// Global data arrays for client-side filtering and actions
let allUsers = [];
let allOrders = [];
let allFeedbacks = [];

// Selected statuses for Segmented Control tabs
let currentUserStatusTab = "all";
let currentOrderStatusTab = "all";
let currentFeedbackStatusTab = "all";

init();

// ==========================================================================
// 1. Initializer & Login Session / MPA Routing
// ==========================================================================
async function init() {
  const path = window.location.pathname;
  const token = localStorage.getItem(tokenKey);

  // If no token exists, redirect any sub-pages to the root login page
  if (!token) {
    if (path !== "/admin") {
      window.location.href = "/admin";
      return;
    }
    setLoggedOutState();
    setupAuthListeners();
    return;
  }

  // Token exists
  setupEventListeners();
  try {
    await loadAdminSession(path);
  } catch (err) {
    console.error("加载管理员会话失败:", err);
    localStorage.removeItem(tokenKey);
    window.location.href = "/admin";
  }
}

async function loadAdminSession(path) {
  const admin = await apiFetch("/api/v1/admin/auth/me");
  
  // Set profile info in sidebar
  if (currentAdminName) currentAdminName.textContent = admin.nickname || "Admin";
  if (currentAdminEmail) currentAdminEmail.textContent = admin.email || "";
  if (adminAvatar) adminAvatar.textContent = (admin.nickname || admin.email || "A").charAt(0).toUpperCase();

  if (loginView) loginView.classList.add("hidden");
  if (dashboardView) dashboardView.classList.remove("hidden");

  // Load data based on path
  if (path === "/admin") {
    await loadOverviewData();
  } else if (path === "/admin/users") {
    await loadUsers();
  } else if (path === "/admin/orders") {
    await loadOrders();
  } else if (path === "/admin/feedbacks") {
    await loadFeedbacks();
  } else if (path === "/admin/sms") {
    await loadSmsLogs();
  } else if (path === "/admin/versions") {
    await loadAppVersions();
  } else if (path === "/admin/plans") {
    await loadMembershipPlans();
  } else if (path === "/admin/active") {
    await loadActiveAnalytics();
  }
}

function setLoggedOutState() {
  if (loginView) loginView.classList.remove("hidden");
  if (dashboardView) dashboardView.classList.add("hidden");
  if (currentAdminName) currentAdminName.textContent = "";
  if (currentAdminEmail) currentAdminEmail.textContent = "";
  if (statsContainer) statsContainer.innerHTML = "";
  if (usersTable) usersTable.innerHTML = "";
  if (ordersTable) ordersTable.innerHTML = "";
  allUsers = [];
  allOrders = [];
}


// ==========================================================================
// 2. Navigation, Auth & Event Listeners
// ==========================================================================
function setupAuthListeners() {
  if (loginForm) {
    loginForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (loginError) loginError.textContent = "";
      const email = document.getElementById("email").value.trim();
      const password = document.getElementById("password").value;

      try {
        const response = await fetch("/api/v1/admin/auth/login", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email, password }),
        });
        const payload = await response.json();
        if (!response.ok) {
          throw new Error(payload.detail || "登录失败");
        }
        localStorage.setItem(tokenKey, payload.access_token);
        // Refresh to trigger logged in view
        window.location.reload();
      } catch (error) {
        if (loginError) loginError.textContent = error.message || "登录失败";
      }
    });
  }
}

function setupEventListeners() {
  // Logout Button
  const logoutBtn = document.getElementById("logout-button");
  if (logoutBtn) {
    logoutBtn.addEventListener("click", () => {
      localStorage.removeItem(tokenKey);
      window.location.href = "/admin";
    });
  }

  // Sidebar Collapse button
  const collapseBtn = document.querySelector(".collapse-btn");
  const sidebar = document.querySelector(".admin-sidebar");
  if (collapseBtn && sidebar) {
    const setSidebarCollapsed = (collapsed) => {
      sidebar.classList.toggle("collapsed", collapsed);
      collapseBtn.setAttribute("aria-expanded", String(!collapsed));
      collapseBtn.setAttribute("aria-label", collapsed ? "展开侧边栏" : "折叠侧边栏");
      collapseBtn.title = collapsed ? "展开侧边栏" : "折叠侧边栏";
    };

    setSidebarCollapsed(localStorage.getItem(sidebarCollapsedKey) === "true");
    collapseBtn.addEventListener("click", () => {
      const collapsed = !sidebar.classList.contains("collapsed");
      setSidebarCollapsed(collapsed);
      localStorage.setItem(sidebarCollapsedKey, String(collapsed));
    });
  }



  // Feedbacks Page Event Listeners
  setupFeedbackPageListeners();

  // Sidebar Menu Group expansion toggles
  document.querySelectorAll(".group-title").forEach((title) => {
    title.addEventListener("click", () => {
      const group = title.closest(".menu-group");
      group.classList.toggle("expanded");
    });
  });

  // User list Filters & Segment Tabs
  setupUserPageListeners();

  // Order list Filters & Segment Tabs
  setupOrderPageListeners();

  // SMS list Filters & Segment Tabs
  setupSmsPageListeners();

  // Active Log Page Event Listeners
  setupActivePageListeners();
}

function setupUserPageListeners() {
  const searchBtn = document.getElementById("search-users-button");
  if (searchBtn) {
    searchBtn.addEventListener("click", () => loadUsers(true));
  }

  const resetBtn = document.getElementById("reset-users-button");
  if (resetBtn) {
    resetBtn.addEventListener("click", () => {
      document.getElementById("user-filter-id").value = "";
      document.getElementById("user-filter-nickname").value = "";
      document.getElementById("user-filter-email").value = "";
      document.getElementById("user-filter-date").value = "";
      loadUsers();
    });
  }

  const limitSel = document.getElementById("user-limit");
  if (limitSel) {
    limitSel.addEventListener("change", () => loadUsers(true));
  }

  document.querySelectorAll("#user-status-tabs .segment").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll("#user-status-tabs .segment").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      currentUserStatusTab = btn.getAttribute("data-status");
      applyUserFilters();
    });
  });

  const exportBtn = document.getElementById("export-users-csv");
  if (exportBtn) {
    exportBtn.addEventListener("click", exportUsersToCsv);
  }
}

function setupOrderPageListeners() {
  const filterBtn = document.getElementById("filter-orders-button");
  if (filterBtn) {
    filterBtn.addEventListener("click", () => loadOrders(true));
  }

  const resetBtn = document.getElementById("reset-orders-button");
  if (resetBtn) {
    resetBtn.addEventListener("click", () => {
      document.getElementById("order-filter-no").value = "";
      document.getElementById("order-filter-email").value = "";
      document.getElementById("order-filter-channel").value = "";
      loadOrders(true);
    });
  }

  const limitSel = document.getElementById("order-limit");
  if (limitSel) {
    limitSel.addEventListener("change", () => loadOrders(true));
  }

  document.querySelectorAll("#order-status-tabs .segment").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll("#order-status-tabs .segment").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      currentOrderStatusTab = btn.getAttribute("data-status");
      applyOrderFilters();
    });
  });

  const exportBtn = document.getElementById("export-orders-csv");
  if (exportBtn) {
    exportBtn.addEventListener("click", exportOrdersToCsv);
  }
}


// ==========================================================================
// 3. Page Data Loading & Render Helpers
// ==========================================================================
async function loadOverviewData() {
  const data = await apiFetch("/api/v1/admin/dashboard");
  renderStats(data.stats);
}

async function loadUsers(forceFetch = false) {
  if (allUsers.length === 0 || forceFetch) {
    const limit = document.getElementById("user-limit").value;
    const emailVal = document.getElementById("user-filter-email").value.trim();
    const nicknameVal = document.getElementById("user-filter-nickname").value.trim();
    
    const searchVal = emailVal || nicknameVal || "";
    let query = `?limit=${limit}`;
    if (searchVal) {
      query += `&search=${encodeURIComponent(searchVal)}`;
    }
    
    allUsers = await apiFetch(`/api/v1/admin/users${query}`);
    allUsers.forEach((u) => {
      if (u.is_active === undefined) u.is_active = true;
    });
  }
  
  applyUserFilters();
}

function applyUserFilters() {
  if (!usersTable) return;

  const idVal = document.getElementById("user-filter-id").value.trim();
  const dateVal = document.getElementById("user-filter-date").value;
  
  // Calculate status counts for segment tabs
  const countAll = allUsers.length;
  const countActive = allUsers.filter(u => u.membership_status === "active").length;
  const countInactive = allUsers.filter(u => u.membership_status !== "active").length;
  
  const bAll = document.getElementById("badge-users-all");
  const bActive = document.getElementById("badge-users-active");
  const bInactive = document.getElementById("badge-users-inactive");
  if (bAll) bAll.textContent = countAll;
  if (bActive) bActive.textContent = countActive;
  if (bInactive) bInactive.textContent = countInactive;

  // Filter list client-side
  let filtered = allUsers.filter((u) => {
    if (idVal && String(u.id) !== idVal) return false;
    if (!checkDateRange(dateVal, u.created_at)) return false;
    
    if (currentUserStatusTab === "active" && u.membership_status !== "active") return false;
    if (currentUserStatusTab === "inactive" && u.membership_status === "active") return false;
    
    return true;
  });

  renderUsers(filtered);
}

function renderUsers(users) {
  if (!usersTable) return;

  usersTable.innerHTML = users
    .map((user) => {
      const firstLetter = (user.nickname || user.username || user.phone || user.email || "?").charAt(0).toUpperCase();
      const bgIndex = user.id % 5;
      const avatarImageHtml = user.avatar_url
        ? `<img class="user-avatar-image" src="${escapeHtml(user.avatar_url)}" alt="" onerror="this.remove()" />`
        : "";
      const avatarHtml = `
        <div class="avatar-circle avatar-bg-${bgIndex}">
          <span>${escapeHtml(firstLetter)}</span>
          ${avatarImageHtml}
        </div>
      `;
      
      const isMemberActive = user.membership_status === "active";
      const membershipClass = isMemberActive ? "tag-active" : "tag-inactive";
      const membershipLabel = user.membership_plan_code
        ? `${user.membership_plan_code} · ${user.membership_status}`
        : "未激活";
      const membershipExpireLabel = isMemberActive
        ? (user.membership_expire_at ? formatDate(user.membership_expire_at) : "永久")
        : "-";

      const statusActionHtml = user.is_active 
        ? `<button class="action-link action-link-danger" onclick="toggleUserStatus(${user.id}, false)">禁用</button>`
        : `<button class="action-link" onclick="toggleUserStatus(${user.id}, true)">启用</button>`;
      const membershipActionHtml = `
        <button class="action-link" onclick="grantMembership(${user.id})">${isMemberActive ? "延长会员" : "赠送会员"}</button>
        ${isMemberActive ? `<button class="action-link action-link-danger" onclick="terminateMembership(${user.id})">终止会员</button>` : ""}
      `;

      return `
        <tr>
          <td>
            <div class="user-cell">
              ${avatarHtml}
              <div class="user-detail">
                <span class="user-name">${escapeHtml(user.nickname || "无昵称")}</span>
                <span class="user-id-sub">ID: ${user.id}</span>
              </div>
            </div>
          </td>
          <td>${escapeHtml(user.username || "-")}</td>
          <td>${escapeHtml(user.phone || "-")}</td>
          <td>${escapeHtml(user.email || "-")}</td>
          <td>
            <span class="tag ${membershipClass}">${escapeHtml(membershipLabel)}</span>
          </td>
          <td>${escapeHtml(membershipExpireLabel)}</td>
          <td>${formatDate(user.created_at)}</td>
          <td class="align-right">
            <div class="table-actions">
              ${membershipActionHtml}
              ${statusActionHtml}
            </div>
          </td>
        </tr>
      `;
    })
    .join("");
}

// Global functions for inline interactive mockup triggers
window.toggleUserStatus = function (userId, state) {
  const user = allUsers.find(u => u.id === userId);
  if (user) {
    user.is_active = state;
    applyUserFilters();
  }
};

function replaceUserSummary(updatedUser) {
  const index = allUsers.findIndex((user) => user.id === updatedUser.id);
  if (index !== -1) allUsers[index] = updatedUser;
  applyUserFilters();
}

window.grantMembership = async function (userId) {
  const user = allUsers.find((item) => item.id === userId);
  if (!user) return;

  const input = window.prompt(
    `${user.membership_status === "active" ? "延长" : "赠送"}会员天数（1–3650 天）`,
    "30",
  );
  if (input === null) return;

  const days = Number(input);
  if (!Number.isInteger(days) || days < 1 || days > 3650) {
    window.alert("请输入 1–3650 之间的整数天数");
    return;
  }
  if (!window.confirm(`确认给用户 ID ${userId} ${user.membership_status === "active" ? "延长" : "赠送"} ${days} 天会员吗？`)) return;

  try {
    const updatedUser = await apiFetch(`/api/v1/admin/users/${userId}/membership`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ days }),
    });
    replaceUserSummary(updatedUser);
    window.alert("会员权益已更新");
  } catch (error) {
    window.alert(error.message || "会员操作失败");
  }
};

window.terminateMembership = async function (userId) {
  if (!window.confirm(`确认提前终止用户 ID ${userId} 的会员权益吗？此操作立即生效。`)) return;

  try {
    const updatedUser = await apiFetch(`/api/v1/admin/users/${userId}/membership`, {
      method: "DELETE",
    });
    replaceUserSummary(updatedUser);
    window.alert("会员权益已终止");
  } catch (error) {
    window.alert(error.message || "终止会员失败");
  }
};

function exportUsersToCsv() {
  const headers = ["用户ID", "账号", "手机号", "邮箱", "昵称", "会员状态", "订阅套餐", "会员到期时间", "注册日期"];
  const rows = allUsers.map((u) => [
    u.id,
    u.username || "",
    u.phone || "",
    u.email || "",
    u.nickname || "",
    u.membership_status || "",
    u.membership_plan_code || "None",
    u.membership_status === "active"
      ? (u.membership_expire_at ? formatDate(u.membership_expire_at) : "永久")
      : "",
    formatDate(u.created_at),
  ]);
  triggerCsvDownload(headers, rows, "users_export.csv");
}


// ==========================================================================
// 4. Order Management & Client Filter
// ==========================================================================
async function loadOrders(forceFetch = false) {
  if (allOrders.length === 0 || forceFetch) {
    const limit = document.getElementById("order-limit").value;
    allOrders = await apiFetch(`/api/v1/admin/orders?limit=${limit}`);
  }

  applyOrderFilters();
}

function applyOrderFilters() {
  if (!ordersTable) return;

  const noVal = document.getElementById("order-filter-no").value.trim().toLowerCase();
  const emailVal = document.getElementById("order-filter-email").value.trim().toLowerCase();
  const channelVal = document.getElementById("order-filter-channel").value;

  // Calculate status counts
  const countAll = allOrders.length;
  const countPaid = allOrders.filter(o => o.status === "paid").length;
  const countPending = allOrders.filter(o => o.status === "pending").length;

  const bAll = document.getElementById("badge-orders-all");
  const bPaid = document.getElementById("badge-orders-paid");
  const bPending = document.getElementById("badge-orders-pending");
  if (bAll) bAll.textContent = countAll;
  if (bPaid) bPaid.textContent = countPaid;
  if (bPending) bPending.textContent = countPending;

  // Filter list client-side
  let filtered = allOrders.filter((o) => {
    if (noVal && !o.order_no.toLowerCase().includes(noVal)) return false;
    if (emailVal && !(o.user_email || "").toLowerCase().includes(emailVal)) return false;
    if (channelVal && o.payment_channel !== channelVal) return false;
    
    if (currentOrderStatusTab === "paid" && o.status !== "paid") return false;
    if (currentOrderStatusTab === "pending" && o.status !== "pending") return false;

    return true;
  });

  renderOrders(filtered);
}

function renderOrders(orders) {
  if (!ordersTable) return;

  ordersTable.innerHTML = orders
    .map((order) => {
      const statusClass = order.status === "paid" ? "tag-paid" : "tag-pending";
      const statusLabel = order.status === "paid" ? "已支付" : "待支付";
      const paymentChannelLabel = order.payment_channel === "alipay" ? "支付宝" : (order.payment_channel === "wxpay" ? "微信支付" : (order.payment_channel || "-"));
      
      return `
        <tr>
          <td><strong>${escapeHtml(order.order_no)}</strong></td>
          <td>${escapeHtml(order.user_email || "-")}</td>
          <td><code>${escapeHtml(order.plan_code || "None")}</code></td>
          <td>¥${formatMoney(order.amount)}</td>
          <td>${escapeHtml(paymentChannelLabel)}</td>
          <td><span class="tag ${statusClass}">${statusLabel}</span></td>
          <td>${formatDate(order.created_at)}</td>
          <td>${formatDate(order.paid_at)}</td>
        </tr>
      `;
    })
    .join("");
}

function exportOrdersToCsv() {
  const headers = ["订单号", "用户账号", "订阅套餐", "交易金额", "支付渠道", "交易状态", "创建时间", "付款时间"];
  const rows = allOrders.map((o) => [
    o.order_no,
    o.user_email || "",
    o.plan_code || "None",
    o.amount,
    o.payment_channel || "",
    o.status || "",
    formatDate(o.created_at),
    formatDate(o.paid_at),
  ]);
  triggerCsvDownload(headers, rows, "orders_export.csv");
}


// ==========================================================================
// 5. Shared Utility Helpers
// ==========================================================================
async function apiFetch(url, options = {}) {
  const token = localStorage.getItem(tokenKey);
  const response = await fetch(url, {
    ...options,
    headers: {
      ...(options.headers || {}),
      Authorization: `Bearer ${token}`,
    },
  });

  if (response.status === 401 || response.status === 403) {
    localStorage.removeItem(tokenKey);
    throw new Error("登录已失效，请重新登录");
  }

  const payload = await response.json();
  if (!response.ok) {
    throw new Error(payload.detail || "请求失败");
  }
  return payload;
}

function formatDate(value) {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString("zh-CN", { hour12: false });
}

function formatMoney(value) {
  const amount = Number(value);
  return Number.isFinite(amount) ? amount.toFixed(2) : value;
}

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function checkDateRange(dateVal, dateStr) {
  if (!dateVal) return true;
  const registeredDate = new Date(dateStr);
  const now = new Date();
  
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);
  
  const sevenDaysAgo = new Date(today);
  sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
  
  const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
  
  if (dateVal === "today") {
    return registeredDate >= today;
  } else if (dateVal === "yesterday") {
    return registeredDate >= yesterday && registeredDate < today;
  } else if (dateVal === "week") {
    return registeredDate >= sevenDaysAgo;
  } else if (dateVal === "month") {
    return registeredDate >= startOfMonth;
  }
  return true;
}

function triggerCsvDownload(headers, rows, filename) {
  const csvContent = "data:text/csv;charset=utf-8,\ufeff" 
    + [headers.join(","), ...rows.map(e => e.join(","))].join("\n");
  const encodedUri = encodeURI(csvContent);
  const link = document.createElement("a");
  link.setAttribute("href", encodedUri);
  link.setAttribute("download", filename);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

function renderStats(stats) {
  if (!statsContainer) return;
  const todaySmsSent = stats.today_sms_sent || 0;
  const todaySmsVerified = stats.today_sms_verified || 0;
  const convRate = todaySmsSent > 0 ? ((todaySmsVerified / todaySmsSent) * 100).toFixed(1) + "%" : "0.0%";

  const categories = [
    {
      title: "👥 用户与活跃",
      items: [
        ["总用户数", stats.total_users],
        ["今日活跃用户", stats.today_active_users || 0],
        ["有效会员数", stats.active_memberships],
      ]
    },
    {
      title: "💰 营收与交易",
      items: [
        ["支付总额", `¥${formatMoney(stats.paid_revenue)}`],
        ["已支付订单", stats.paid_orders],
        ["订单总数", stats.total_orders],
      ]
    },
    {
      title: "⚙️ 系统与通道",
      items: [
        ["今日发送短信", todaySmsSent],
        ["今日发送失败", stats.today_sms_failed || 0],
        ["今日短信验证率", convRate],
      ]
    }
  ];

  let html = "";
  categories.forEach(cat => {
    const cardsHtml = cat.items
      .map(
        ([label, value]) => `
          <article class="stat-card">
            <span>${escapeHtml(label)}</span>
            <strong>${escapeHtml(String(value))}</strong>
          </article>
        `
      )
      .join("");

    html += `
      <section class="stats-group">
        <h3 class="stats-group-title">${escapeHtml(cat.title)}</h3>
        <div class="stats-grid">
          ${cardsHtml}
        </div>
      </section>
    `;
  });

  statsContainer.innerHTML = html;
}

// ==========================================================================
// 6. SMS Operations & View
// ==========================================================================
let currentSmsPage = 1;
let currentSmsStatusTab = "all";
let allSmsLogs = [];
let totalSmsCount = 0;

async function loadSmsLogs(forceFetch = false) {
  const limitSel = document.getElementById("sms-limit");
  const limit = limitSel ? parseInt(limitSel.value, 10) : 50;
  const offset = (currentSmsPage - 1) * limit;

  const searchInput = document.getElementById("sms-filter-search");
  const searchVal = searchInput ? searchInput.value.trim() : "";

  let query = `?limit=${limit}&offset=${offset}`;
  if (searchVal) {
    query += `&search=${encodeURIComponent(searchVal)}`;
  }
  if (currentSmsStatusTab !== "all") {
    query += `&status=${currentSmsStatusTab}`;
  }

  const tbody = document.getElementById("sms-table-body");
  if (tbody) {
    tbody.innerHTML = `
      <tr>
        <td colspan="8" style="text-align: center; color: var(--text-secondary); padding: 24px 0;">正在加载中...</td>
      </tr>
    `;
  }

  try {
    const data = await apiFetch(`/api/v1/admin/sms${query}`);
    allSmsLogs = data.items || [];
    totalSmsCount = data.total || 0;

    renderSmsLogs();
    updateSmsPagination();
  } catch (error) {
    console.error("加载短信记录失败:", error);
    if (tbody) {
      tbody.innerHTML = `
        <tr>
          <td colspan="8" style="text-align: center; color: var(--error); padding: 24px 0;">加载失败: ${escapeHtml(error.message)}</td>
        </tr>
      `;
    }
  }
}

function setupSmsPageListeners() {
  const searchBtn = document.getElementById("search-sms-button");
  if (searchBtn) {
    searchBtn.addEventListener("click", () => {
      currentSmsPage = 1;
      loadSmsLogs();
    });
  }

  const resetBtn = document.getElementById("reset-sms-button");
  if (resetBtn) {
    resetBtn.addEventListener("click", () => {
      const searchInput = document.getElementById("sms-filter-search");
      if (searchInput) searchInput.value = "";
      currentSmsPage = 1;
      loadSmsLogs();
    });
  }

  const limitSel = document.getElementById("sms-limit");
  if (limitSel) {
    limitSel.addEventListener("change", () => {
      currentSmsPage = 1;
      loadSmsLogs();
    });
  }

  document.querySelectorAll("#sms-status-tabs .segment").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll("#sms-status-tabs .segment").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      currentSmsStatusTab = btn.getAttribute("data-status");
      currentSmsPage = 1;
      loadSmsLogs();
    });
  });

  const prevBtn = document.getElementById("prev-sms-page");
  const nextBtn = document.getElementById("next-sms-page");

  if (prevBtn) {
    prevBtn.addEventListener("click", () => {
      if (currentSmsPage > 1) {
        currentSmsPage--;
        loadSmsLogs();
      }
    });
  }

  if (nextBtn) {
    nextBtn.addEventListener("click", () => {
      const limitSel = document.getElementById("sms-limit");
      const limit = limitSel ? parseInt(limitSel.value, 10) : 50;
      if (currentSmsPage * limit < totalSmsCount) {
        currentSmsPage++;
        loadSmsLogs();
      }
    });
  }
}

function updateSmsPagination() {
  const pageInfo = document.getElementById("sms-page-info");
  const prevBtn = document.getElementById("prev-sms-page");
  const nextBtn = document.getElementById("next-sms-page");

  const limitSel = document.getElementById("sms-limit");
  const limit = limitSel ? parseInt(limitSel.value, 10) : 50;
  const totalPages = Math.ceil(totalSmsCount / limit) || 1;

  if (pageInfo) {
    pageInfo.textContent = `共 ${totalSmsCount} 条记录，当前第 ${currentSmsPage} / ${totalPages} 页`;
  }

  if (prevBtn) {
    prevBtn.disabled = currentSmsPage <= 1;
  }

  if (nextBtn) {
    nextBtn.disabled = currentSmsPage * limit >= totalSmsCount;
  }
}

function renderSmsLogs() {
  const tbody = document.getElementById("sms-table-body");
  if (!tbody) return;

  const badgeAll = document.getElementById("badge-sms-all");
  const badgeSuccess = document.getElementById("badge-sms-success");
  const badgeFailed = document.getElementById("badge-sms-failed");
  const badgePending = document.getElementById("badge-sms-pending");

  if (currentSmsStatusTab === "all" && badgeAll) badgeAll.textContent = totalSmsCount;
  if (currentSmsStatusTab === "success" && badgeSuccess) badgeSuccess.textContent = totalSmsCount;
  if (currentSmsStatusTab === "failed" && badgeFailed) badgeFailed.textContent = totalSmsCount;
  if (currentSmsStatusTab === "pending" && badgePending) badgePending.textContent = totalSmsCount;

  if (allSmsLogs.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="8" style="text-align: center; color: var(--text-secondary); padding: 24px 0;">暂无短信记录</td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = allSmsLogs
    .map((sms) => {
      const dateStr = formatDate(sms.created_at);

      let phoneDisplay = sms.phone || "";
      if (phoneDisplay.length === 11) {
        phoneDisplay = phoneDisplay.replace(/(\d{3})\d{4}(\d{4})/, "$1****$2");
      }

      let purposeLabel = sms.purpose;
      if (sms.purpose === "login") {
        purposeLabel = "登录/注册";
      } else if (sms.purpose === "bind_phone") {
        purposeLabel = "绑定手机";
      } else if (sms.purpose === "reset_password") {
        purposeLabel = "修改密码";
      }

      let statusHtml = "";
      if (sms.delivery_status === "sent" || sms.delivery_status === "success") {
        statusHtml = `<span class="tag tag-active">发送成功</span>`;
      } else if (sms.delivery_status === "failed") {
        statusHtml = `<span class="tag tag-disabled">发送失败</span>`;
      } else {
        statusHtml = `<span class="tag tag-inactive">${escapeHtml(sms.delivery_status)}</span>`;
      }

      const consumedHtml = sms.consumed_at
        ? `<span class="tag tag-active">已验证</span>`
        : `<span class="tag tag-user">未使用</span>`;

      const msgDisplay = sms.delivery_message || "-";

      return `
        <tr>
          <td>${escapeHtml(dateStr)}</td>
          <td><strong>${escapeHtml(phoneDisplay)}</strong></td>
          <td>${escapeHtml(purposeLabel)}</td>
          <td>${statusHtml}</td>
          <td><span title="${escapeHtml(msgDisplay)}" style="max-width: 180px; display: inline-block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${escapeHtml(msgDisplay)}</span></td>
          <td>${escapeHtml(sms.request_ip || "-")}</td>
          <td>${sms.failed_attempts}</td>
          <td>${consumedHtml}</td>
        </tr>
      `;
    })
    .join("");
}

// ==========================================================================
// 6. Feedback Operations & View
// ==========================================================================
async function loadFeedbacks(forceFetch = false) {
  if (allFeedbacks.length === 0 || forceFetch) {
    allFeedbacks = await apiFetch("/api/v1/admin/feedbacks?limit=100");
  }
  applyFeedbackFilters();
}

function setupFeedbackPageListeners() {
  const segAll = document.getElementById("segment-feedbacks-all");
  const segPending = document.getElementById("segment-feedbacks-pending");
  const segResolved = document.getElementById("segment-feedbacks-resolved");

  if (!segAll) return;

  const resetActiveSegment = (activeBtn) => {
    [segAll, segPending, segResolved].forEach((btn) => btn.classList.remove("active"));
    activeBtn.classList.add("active");
  };

  segAll.addEventListener("click", () => {
    currentFeedbackStatusTab = "all";
    resetActiveSegment(segAll);
    applyFeedbackFilters();
  });

  segPending.addEventListener("click", () => {
    currentFeedbackStatusTab = "pending";
    resetActiveSegment(segPending);
    applyFeedbackFilters();
  });

  segResolved.addEventListener("click", () => {
    currentFeedbackStatusTab = "resolved";
    resetActiveSegment(segResolved);
    applyFeedbackFilters();
  });
}

function applyFeedbackFilters() {
  if (!feedbacksTable) return;

  // Calculate status counts
  const countAll = allFeedbacks.length;
  const countPending = allFeedbacks.filter((f) => f.status === "pending").length;
  const countResolved = allFeedbacks.filter((f) => f.status === "resolved").length;

  const bAll = document.getElementById("badge-feedbacks-all");
  const bPending = document.getElementById("badge-feedbacks-pending");
  const bResolved = document.getElementById("badge-feedbacks-resolved");

  if (bAll) bAll.textContent = countAll;
  if (bPending) bPending.textContent = countPending;
  if (bResolved) bResolved.textContent = countResolved;

  // Filter list client-side
  let filtered = allFeedbacks.filter((f) => {
    if (currentFeedbackStatusTab === "pending" && f.status !== "pending") return false;
    if (currentFeedbackStatusTab === "resolved" && f.status !== "resolved") return false;
    return true;
  });

  renderFeedbacks(filtered);
}

function renderFeedbacks(feedbacks) {
  if (!feedbacksTable) return;

  if (feedbacks.length === 0) {
    feedbacksTable.innerHTML = `
      <tr>
        <td colspan="7" class="align-center" style="color:var(--color-text-muted); padding:30px;">
          暂无符合条件的意见反馈
        </td>
      </tr>
    `;
    return;
  }

  feedbacksTable.innerHTML = feedbacks
    .map((f) => {
      const statusClass = f.status === "resolved" ? "tag-paid" : "tag-pending";
      const statusLabel = f.status === "resolved" ? "已解决" : "待处理";
      
      const actionHtml = f.status === "pending"
        ? `<button class="action-link" onclick="resolveFeedback(${f.id})">解决</button>`
        : `<span style="color:var(--color-text-muted); font-size:12px;">已完成</span>`;

      let userHtml = `<span style="color:var(--color-text-muted);">游客</span>`;
      if (f.user_id) {
        userHtml = `
          <div style="text-align: left; line-height: 1.3;">
            <div style="font-weight: 600;">${escapeHtml(f.user_nickname || "-")}</div>
            <div style="font-size: 11px; color: var(--color-text-muted);">UID: ${f.user_id}</div>
            ${f.user_phone ? `<div style="font-size: 11px; color: var(--color-text-muted);">${escapeHtml(f.user_phone)}</div>` : ""}
          </div>
        `;
      }

      return `
        <tr>
          <td><code>#${f.id}</code></td>
          <td style="white-space: pre-wrap; max-width: 400px; text-align: left;">${escapeHtml(f.content)}</td>
          <td>${userHtml}</td>
          <td>${escapeHtml(f.contact || "-")}</td>
          <td><span class="tag ${statusClass}">${statusLabel}</span></td>
          <td>${formatDate(f.created_at)}</td>
          <td class="align-right">${actionHtml}</td>
        </tr>
      `;
    })
    .join("");
}

window.resolveFeedback = async function (feedbackId) {
  if (!window.confirm(`确认标记反馈工单 #${feedbackId} 为已解决状态吗？`)) return;

  try {
    const updatedFeedback = await apiFetch(`/api/v1/admin/feedbacks/${feedbackId}/resolve`, {
      method: "POST",
    });
    
    // Update local list
    allFeedbacks = allFeedbacks.map((f) => (f.id === feedbackId ? updatedFeedback : f));
    applyFeedbackFilters();
    window.alert("反馈状态已更新");
  } catch (error) {
    window.alert(error.message || "更新反馈状态失败");
  }
};

// ==========================================================================
// 7. App Versions Operations & View
// ==========================================================================
let allVersions = [];

async function loadAppVersions() {
  const tbody = document.getElementById("versions-table-body");
  if (tbody) {
    tbody.innerHTML = `
      <tr>
        <td colspan="7" style="text-align: center; color: var(--text-secondary); padding: 24px 0;">正在加载中...</td>
      </tr>
    `;
  }

  try {
    const data = await apiFetch("/api/v1/admin/app-versions");
    allVersions = data || [];
    renderAppVersions();
    setupVersionEventListeners();
  } catch (error) {
    console.error("加载版本记录失败:", error);
    if (tbody) {
      tbody.innerHTML = `
        <tr>
          <td colspan="7" style="text-align: center; color: var(--error); padding: 24px 0;">加载失败: ${escapeHtml(error.message)}</td>
        </tr>
      `;
    }
  }
}

function renderAppVersions() {
  const tbody = document.getElementById("versions-table-body");
  if (!tbody) return;

  if (allVersions.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="7" style="text-align: center; color: var(--text-secondary); padding: 24px 0;">暂无版本记录</td>
      </tr>
    `;
    return;
  }

  let html = "";
  allVersions.forEach((v) => {
    const isForce = v.is_force_update 
      ? '<span class="tag tag-disabled">是</span>' 
      : '<span class="tag tag-user">否</span>';
      
    html += `
      <tr>
        <td><code>${escapeHtml(v.version_code.toString())}</code></td>
        <td><strong>v${escapeHtml(v.version_name)}</strong></td>
        <td><a href="${escapeHtml(v.download_url)}" target="_blank" style="color: var(--primary); text-decoration: none;">${escapeHtml(v.download_url)}</a></td>
        <td style="white-space: pre-wrap;">${escapeHtml(v.update_log)}</td>
        <td style="text-align: center;">${isForce}</td>
        <td>${formatDate(v.created_at)}</td>
        <td style="text-align: center;">
          <button class="action-link action-link-danger delete-version-btn" data-code="${v.version_code}" type="button">删除</button>
        </td>
      </tr>
    `;
  });
  tbody.innerHTML = html;

  // Bind delete events
  document.querySelectorAll(".delete-version-btn").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const code = btn.getAttribute("data-code");
      if (confirm(`确定要删除版本代码为 ${code} 的发布记录吗？`)) {
        try {
          const res = await fetch(`/api/v1/admin/app-versions/${code}`, {
            method: "DELETE",
            headers: {
              "Authorization": `Bearer ${localStorage.getItem(tokenKey)}`
            }
          });
          if (!res.ok) {
            const errData = await res.json();
            throw new Error(errData.detail || "删除失败");
          }
          await loadAppVersions();
        } catch (err) {
          alert(`删除失败: ${err.message}`);
        }
      }
    });
  });
}

function setupVersionEventListeners() {
  const publishBtn = document.getElementById("publish-version-btn");
  const dialog = document.getElementById("version-dialog");
  const cancelBtn = document.getElementById("cancel-version-btn");
  const form = document.getElementById("version-form");

  if (publishBtn && dialog) {
    publishBtn.replaceWith(publishBtn.cloneNode(true));
    document.getElementById("publish-version-btn").addEventListener("click", () => {
      form.reset();
      dialog.showModal();
    });
  }

  if (cancelBtn && dialog) {
    cancelBtn.replaceWith(cancelBtn.cloneNode(true));
    document.getElementById("cancel-version-btn").addEventListener("click", () => {
      dialog.close();
    });
  }

  if (form && dialog) {
    form.replaceWith(form.cloneNode(true));
    const newForm = document.getElementById("version-form");
    newForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      const code = parseInt(document.getElementById("form-version-code").value, 10);
      const name = document.getElementById("form-version-name").value.trim();
      const url = document.getElementById("form-download-url").value.trim();
      const log = document.getElementById("form-update-log").value;
      const isForce = document.getElementById("form-is-force-update").checked;

      try {
        const response = await fetch("/api/v1/admin/app-versions", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${localStorage.getItem(tokenKey)}`
          },
          body: JSON.stringify({
            version_code: code,
            version_name: name,
            download_url: url,
            update_log: log,
            is_force_update: isForce
          })
        });

        if (!response.ok) {
          const errData = await response.json();
          throw new Error(errData.detail || "发布失败");
        }

        dialog.close();
        await loadAppVersions();
      } catch (err) {
        alert(err.message);
      }
    });
  }
}


// ==========================================================================
// 8. Membership Plans Operations & View
// ==========================================================================
let allPlans = [];

async function loadMembershipPlans() {
  const tbody = document.getElementById("plans-table-body");
  if (tbody) {
    tbody.innerHTML = `
      <tr>
        <td colspan="11" style="text-align: center; color: var(--text-secondary); padding: 24px 0;">正在加载中...</td>
      </tr>
    `;
  }

  try {
    const data = await apiFetch("/api/v1/admin/membership-plans");
    allPlans = data || [];
    renderMembershipPlans();
    setupPlanEventListeners();
  } catch (error) {
    console.error("加载会员套餐失败:", error);
    if (tbody) {
      tbody.innerHTML = `
        <tr>
          <td colspan="11" style="text-align: center; color: var(--error); padding: 24px 0;">加载失败: ${escapeHtml(error.message)}</td>
        </tr>
      `;
    }
  }
}

function renderMembershipPlans() {
  const tbody = document.getElementById("plans-table-body");
  if (!tbody) return;

  if (allPlans.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="11" style="text-align: center; color: var(--text-secondary); padding: 24px 0;">暂无套餐记录</td>
      </tr>
    `;
    return;
  }

  let html = "";
  allPlans.forEach((p) => {
    const originalPriceText = p.original_price !== null ? `￥${parseFloat(p.original_price).toFixed(2)}` : "-";
    const durationText = p.duration_days !== null ? `${p.duration_days} 天` : "永久/终身";
    const isDefaultTag = p.is_default 
      ? '<span class="tag tag-user">是</span>' 
      : '<span class="tag tag-disabled">否</span>';
    const isActiveTag = p.is_active 
      ? '<span class="tag tag-user">已上架</span>' 
      : '<span class="tag tag-disabled">已下架</span>';
      
    html += `
      <tr>
        <td><code>${escapeHtml(p.plan_code)}</code></td>
        <td><strong>${escapeHtml(p.title)}</strong></td>
        <td>￥${parseFloat(p.price).toFixed(2)}</td>
        <td>${originalPriceText}</td>
        <td>${durationText}</td>
        <td>${escapeHtml(p.bottom_label)}</td>
        <td>${p.tag_text ? `<span style="background: #fff2e8; color: #fa541c; border: 1px solid #ffd8bf; padding: 2px 6px; border-radius: 4px; font-size: 12px;">${escapeHtml(p.tag_text)}</span>` : "-"}</td>
        <td style="text-align: center;">${isDefaultTag}</td>
        <td style="text-align: center;">${isActiveTag}</td>
        <td style="text-align: center;"><code>${p.sort_order}</code></td>
        <td style="text-align: center;">
          <button class="action-link edit-plan-btn" data-code="${escapeHtml(p.plan_code)}" type="button">编辑</button>
        </td>
      </tr>
    `;
  });
  tbody.innerHTML = html;

  // Bind edit events
  document.querySelectorAll(".edit-plan-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      const code = btn.getAttribute("data-code");
      const plan = allPlans.find((item) => item.plan_code === code);
      if (plan) {
        showEditPlanDialog(plan);
      }
    });
  });
}

function showEditPlanDialog(plan) {
  const dialog = document.getElementById("plan-dialog");
  if (!dialog) return;

  document.getElementById("form-plan-code").value = plan.plan_code;
  document.getElementById("form-plan-title").value = plan.title;
  document.getElementById("form-plan-price").value = parseFloat(plan.price);
  document.getElementById("form-plan-original-price").value = plan.original_price !== null ? parseFloat(plan.original_price) : "";
  document.getElementById("form-plan-duration").value = plan.duration_days !== null ? plan.duration_days : "";
  document.getElementById("form-plan-bottom-label").value = plan.bottom_label;
  document.getElementById("form-plan-tag-text").value = plan.tag_text || "";
  document.getElementById("form-plan-sort-order").value = plan.sort_order;
  document.getElementById("form-plan-is-default").checked = plan.is_default;
  document.getElementById("form-plan-is-active").checked = plan.is_active;

  dialog.showModal();
}

function setupPlanEventListeners() {
  const dialog = document.getElementById("plan-dialog");
  const cancelBtn = document.getElementById("cancel-plan-btn");
  const form = document.getElementById("plan-form");

  if (cancelBtn && dialog) {
    cancelBtn.replaceWith(cancelBtn.cloneNode(true));
    document.getElementById("cancel-plan-btn").addEventListener("click", () => {
      dialog.close();
    });
  }

  if (form && dialog) {
    form.replaceWith(form.cloneNode(true));
    const newForm = document.getElementById("plan-form");
    newForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      const code = document.getElementById("form-plan-code").value;
      const title = document.getElementById("form-plan-title").value.trim();
      const price = parseFloat(document.getElementById("form-plan-price").value);
      
      const originalPriceVal = document.getElementById("form-plan-original-price").value;
      const original_price = originalPriceVal !== "" ? parseFloat(originalPriceVal) : null;
      
      const durationVal = document.getElementById("form-plan-duration").value;
      const duration_days = durationVal !== "" ? parseInt(durationVal, 10) : null;
      
      const bottom_label = document.getElementById("form-plan-bottom-label").value.trim();
      const tag_text = document.getElementById("form-plan-tag-text").value.trim() || null;
      const sort_order = parseInt(document.getElementById("form-plan-sort-order").value, 10);
      const is_default = document.getElementById("form-plan-is-default").checked;
      const is_active = document.getElementById("form-plan-is-active").checked;

      try {
        const response = await fetch(`/api/v1/admin/membership-plans/${code}`, {
          method: "PUT",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${localStorage.getItem(tokenKey)}`
          },
          body: JSON.stringify({
            title,
            price,
            original_price,
            duration_days,
            bottom_label,
            tag_text,
            is_default,
            is_active,
            sort_order
          })
        });

        if (!response.ok) {
          const errData = await response.json();
          throw new Error(errData.detail || "保存失败");
        }

        dialog.close();
        await loadMembershipPlans();
      } catch (err) {
        alert(err.message);
      }
    });
  }
}


// ==========================================================================
// 9. Active User Analytics Operations & View
// ==========================================================================
let currentActivePage = 1;
let allActiveLogs = [];
let totalActiveLogs = 0;
let activeTrends = [];

async function loadActiveAnalytics(forceFetch = false) {
  const trendDaysSelect = document.getElementById("trend-days-select");
  const days = trendDaysSelect ? parseInt(trendDaysSelect.value, 10) : 15;
  
  try {
    activeTrends = await apiFetch(`/api/v1/admin/active/trends?days=${days}`);
    renderActiveTrends();
    renderActiveMiniStats();
  } catch (error) {
    console.error("加载活跃趋势失败:", error);
  }

  await loadActiveLogs(forceFetch);
}

function renderActiveMiniStats() {
  if (activeTrends.length === 0) return;
  
  const todayCount = activeTrends[activeTrends.length - 1]?.count || 0;
  const yesterdayCount = activeTrends[activeTrends.length - 2]?.count || 0;
  
  const last7 = activeTrends.slice(-7);
  const sum = last7.reduce((acc, curr) => acc + curr.count, 0);
  const avg = last7.length > 0 ? Math.round(sum / last7.length) : 0;
  
  const todayEl = document.getElementById("stats-dau-today");
  const yesterdayEl = document.getElementById("stats-dau-yesterday");
  const avgEl = document.getElementById("stats-dau-weekly-avg");
  
  if (todayEl) todayEl.textContent = todayCount;
  if (yesterdayEl) yesterdayEl.textContent = yesterdayCount;
  if (avgEl) avgEl.textContent = avg;
}

function renderActiveTrends() {
  const container = document.getElementById("trend-bars");
  if (!container) return;
  
  if (activeTrends.length === 0) {
    container.innerHTML = `<div style="text-align: center; width: 100%; color: var(--text-secondary); padding: 40px 0;">无趋势数据</div>`;
    return;
  }
  
  const maxCount = Math.max(...activeTrends.map(t => t.count), 1);
  
  let html = "";
  activeTrends.forEach(t => {
    const dateParts = t.active_date.split("-");
    const label = dateParts.length === 3 ? `${dateParts[1]}/${dateParts[2]}` : t.active_date;
    const heightPercent = (t.count / maxCount) * 100;
    
    html += `
      <div class="trend-bar-wrapper">
        <span class="trend-bar-tooltip">${t.active_date}: ${t.count}人活跃</span>
        <div class="trend-bar" style="height: ${Math.max(heightPercent, 2)}%;"></div>
        <span class="trend-date">${label}</span>
      </div>
    `;
  });
  
  container.innerHTML = html;
}

async function loadActiveLogs(forceFetch = false) {
  const limitSel = document.getElementById("active-limit");
  const limit = limitSel ? parseInt(limitSel.value, 10) : 50;
  const offset = (currentActivePage - 1) * limit;
  
  const searchInput = document.getElementById("active-filter-search");
  const searchVal = searchInput ? searchInput.value.trim() : "";
  
  const dateInput = document.getElementById("active-filter-date");
  const dateVal = dateInput ? dateInput.value : "";
  
  let query = `?limit=${limit}&offset=${offset}`;
  if (searchVal) {
    query += `&search=${encodeURIComponent(searchVal)}`;
  }
  if (dateVal) {
    query += `&active_date=${dateVal}`;
  }
  
  const tbody = document.getElementById("active-table-body");
  if (tbody) {
    tbody.innerHTML = `
      <tr>
        <td colspan="7" style="text-align: center; color: var(--text-secondary); padding: 24px 0;">正在加载中...</td>
      </tr>
    `;
  }
  
  try {
    const data = await apiFetch(`/api/v1/admin/active/logs${query}`);
    allActiveLogs = data.items || [];
    totalActiveLogs = data.total || 0;
    
    renderActiveLogs();
    updateActivePagination();
  } catch (error) {
    console.error("加载活跃明细失败:", error);
    if (tbody) {
      tbody.innerHTML = `
        <tr>
          <td colspan="7" style="text-align: center; color: var(--error); padding: 24px 0;">加载失败: ${escapeHtml(error.message)}</td>
        </tr>
      `;
    }
  }
}

function renderActiveLogs() {
  const tbody = document.getElementById("active-table-body");
  if (!tbody) return;
  
  if (allActiveLogs.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="7" style="text-align: center; color: var(--text-secondary); padding: 24px 0;">暂无活跃记录</td>
      </tr>
    `;
    return;
  }
  
  let html = "";
  allActiveLogs.forEach(log => {
    html += `
      <tr>
        <td><code>${log.id}</code></td>
        <td><code>${log.user_id}</code></td>
        <td><strong>${escapeHtml(log.user_nickname)}</strong></td>
        <td>${escapeHtml(log.user_phone || "-")}</td>
        <td>${escapeHtml(log.user_email || "-")}</td>
        <td><span class="tag tag-user">${log.active_date}</span></td>
        <td>${formatDate(log.created_at)}</td>
      </tr>
    `;
  });
  tbody.innerHTML = html;
}

function updateActivePagination() {
  const infoEl = document.getElementById("active-pagination-info");
  const prevBtn = document.getElementById("prev-active-btn");
  const nextBtn = document.getElementById("next-active-btn");
  const limitSel = document.getElementById("active-limit");
  const limit = limitSel ? parseInt(limitSel.value, 10) : 50;
  
  if (!infoEl) return;
  
  if (totalActiveLogs === 0) {
    infoEl.textContent = "显示 0 条，共 0 条记录";
    if (prevBtn) prevBtn.disabled = true;
    if (nextBtn) nextBtn.disabled = true;
    return;
  }
  
  const start = (currentActivePage - 1) * limit + 1;
  const end = Math.min(currentActivePage * limit, totalActiveLogs);
  
  infoEl.textContent = `显示 ${start}-${end} 条，共 ${totalActiveLogs} 条记录`;
  
  if (prevBtn) prevBtn.disabled = currentActivePage === 1;
  if (nextBtn) nextBtn.disabled = end >= totalActiveLogs;
}

function setupActivePageListeners() {
  const searchBtn = document.getElementById("search-active-button");
  const resetBtn = document.getElementById("reset-active-button");
  const limitSel = document.getElementById("active-limit");
  const prevBtn = document.getElementById("prev-active-btn");
  const nextBtn = document.getElementById("next-active-btn");
  const trendDaysSelect = document.getElementById("trend-days-select");
  
  if (searchBtn) {
    searchBtn.addEventListener("click", () => {
      currentActivePage = 1;
      loadActiveLogs();
    });
  }
  
  if (resetBtn) {
    resetBtn.addEventListener("click", () => {
      const searchInput = document.getElementById("active-filter-search");
      const dateInput = document.getElementById("active-filter-date");
      if (searchInput) searchInput.value = "";
      if (dateInput) dateInput.value = "";
      currentActivePage = 1;
      loadActiveLogs();
    });
  }
  
  if (limitSel) {
    limitSel.addEventListener("change", () => {
      currentActivePage = 1;
      loadActiveLogs();
    });
  }
  
  if (prevBtn) {
    prevBtn.addEventListener("click", () => {
      if (currentActivePage > 1) {
        currentActivePage--;
        loadActiveLogs();
      }
    });
  }
  
  if (nextBtn) {
    nextBtn.addEventListener("click", () => {
      const limit = limitSel ? parseInt(limitSel.value, 10) : 50;
      if (currentActivePage * limit < totalActiveLogs) {
        currentActivePage++;
        loadActiveLogs();
      }
    });
  }
  
  if (trendDaysSelect) {
    trendDaysSelect.addEventListener("change", () => {
      loadActiveAnalytics();
    });
  }
}
