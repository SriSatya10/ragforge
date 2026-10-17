// RagForge Frontend Client Logic
const API_BASE = "http://localhost:8080";

// App State
let currentToken = localStorage.getItem("ragforge_token") || "";
let currentTenantEmail = localStorage.getItem("ragforge_email") || "";
let currentTenantName = localStorage.getItem("ragforge_name") || "";
let authMode = "login"; // 'login' or 'register'
let uploadedDocs = JSON.parse(localStorage.getItem("ragforge_docs") || "[]");

// Initialize on DOM load
document.addEventListener("DOMContentLoaded", () => {
  setupDropzone();
  checkAuth();
  renderDocsTable();
});

// Toast System
function showToast(message, isError = false) {
  const toast = document.getElementById("toast");
  toast.innerText = message;
  toast.style.borderColor = isError ? "var(--danger)" : "var(--accent-cyan)";
  toast.style.display = "block";
  setTimeout(() => {
    toast.style.display = "none";
  }, 4000);
}

// Navigation View Switcher
function switchView(viewName) {
  const views = ["chat", "docs", "usage", "obs"];
  views.forEach((v) => {
    const section = document.getElementById(`view-v` === `view-${v}` ? `view-${v}` : `view-${v}`);
    const navBtn = document.getElementById(`nav-${v}`);
    if (v === viewName) {
      document.getElementById(`view-${v}`).classList.add("active");
      navBtn.classList.add("active");
    } else {
      document.getElementById(`view-${v}`).classList.remove("active");
      navBtn.classList.remove("active");
    }
  });

  const titles = {
    chat: "RAG Chat Console",
    docs: "Documents & Knowledge Base",
    usage: "Telemetry & Usage Analytics",
    obs: "System Observability & Tracing"
  };
  document.getElementById("page-title").innerText = titles[viewName] || "RagForge";

  if (viewName === "usage") {
    fetchTenantUsage();
  }
}

// --- Authentication ---
function checkAuth() {
  const modal = document.getElementById("auth-modal");
  const displayName = document.getElementById("tenant-display-name");
  const displayEmail = document.getElementById("tenant-display-email");

  if (!currentToken) {
    modal.style.display = "flex";
    displayName.innerText = "Not Signed In";
    displayEmail.innerText = "Sign in to start querying";
  } else {
    modal.style.display = "none";
    displayName.innerText = currentTenantName || "Active Tenant";
    displayEmail.innerText = currentTenantEmail || "Authenticated";
  }
}

function toggleAuthTab(mode) {
  authMode = mode;
  document.getElementById("tab-login").classList.toggle("active", mode === "login");
  document.getElementById("tab-register").classList.toggle("active", mode === "register");
  document.getElementById("group-tenant-name").style.display = mode === "register" ? "block" : "none";
  document.getElementById("btn-auth-submit").innerText = mode === "register" ? "Register & Sign In" : "Sign In";
}

async function handleAuthSubmit(e) {
  e.preventDefault();
  const email = document.getElementById("auth-email").value.trim();
  const password = document.getElementById("auth-password").value.trim();
  const name = document.getElementById("auth-name").value.trim();

  const endpoint = authMode === "register" ? "/auth/register" : "/auth/login";
  const payload = authMode === "register" 
    ? { name: name || "Tenant-" + Date.now(), email, password }
    : { email, password };

  try {
    const res = await fetch(`${API_BASE}${endpoint}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });

    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.message || data.error || "Authentication failed");
    }

    currentToken = data.token;
    currentTenantEmail = data.email || email;
    currentTenantName = name || (authMode === "register" ? payload.name : "Tenant");

    localStorage.setItem("ragforge_token", currentToken);
    localStorage.setItem("ragforge_email", currentTenantEmail);
    localStorage.setItem("ragforge_name", currentTenantName);

    showToast(`Signed in as ${currentTenantEmail}`);
    checkAuth();
  } catch (err) {
    showToast(err.message, true);
  }
}

function logout() {
  localStorage.removeItem("ragforge_token");
  localStorage.removeItem("ragforge_email");
  localStorage.removeItem("ragforge_name");
  currentToken = "";
  currentTenantEmail = "";
  currentTenantName = "";
  checkAuth();
}

// --- RAG Chat Console ---
async function handleSendQuery(e) {
  e.preventDefault();
  const input = document.getElementById("query-input");
  const question = input.value.trim();
  if (!question) return;

  if (!currentToken) {
    showToast("Please sign in first", true);
    checkAuth();
    return;
  }

  // Append user bubble
  appendMessage("user", question);
  input.value = "";

  // Append temporary loading bubble
  const loadingId = "loading-" + Date.now();
  appendLoadingMessage(loadingId);

  const startTime = performance.now();
  try {
    const res = await fetch(`${API_BASE}/query`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${currentToken}`
      },
      body: JSON.stringify({ question, topK: 3 })
    });

    if (res.status === 403 || res.status === 401) {
      removeMessage(loadingId);
      showToast("Session expired, please sign in again", true);
      logout();
      return;
    }

    const data = await res.json();
    const elapsed = Math.round(performance.now() - startTime);

    removeMessage(loadingId);
    appendAssistantResponse(data, elapsed);
  } catch (err) {
    removeMessage(loadingId);
    appendMessage("assistant", `⚠️ Failed to get answer: ${err.message}`);
  }
}

function appendMessage(role, text) {
  const container = document.getElementById("chat-messages");
  const row = document.createElement("div");
  row.className = `message-row ${role}`;
  row.innerHTML = `
    <div class="message-avatar">${role === "user" ? "👤" : "⚡"}</div>
    <div class="message-bubble">${escapeHtml(text)}</div>
  `;
  container.appendChild(row);
  container.scrollTop = container.scrollHeight;
}

function appendLoadingMessage(id) {
  const container = document.getElementById("chat-messages");
  const row = document.createElement("div");
  row.className = "message-row assistant";
  row.id = id;
  row.innerHTML = `
    <div class="message-avatar">⚡</div>
    <div class="message-bubble" style="display:flex; align-items:center; gap:8px;">
      <span class="status-dot"></span>
      <span>Thinking & searching knowledge base...</span>
    </div>
  `;
  container.appendChild(row);
  container.scrollTop = container.scrollHeight;
}

function removeMessage(id) {
  const el = document.getElementById(id);
  if (el) el.remove();
}

function appendAssistantResponse(data, elapsedMs) {
  const container = document.getElementById("chat-messages");
  const row = document.createElement("div");
  row.className = "message-row assistant";

  const isCacheHit = data.cacheHit === true;
  const citations = data.citations || [];

  let citationsHtml = "";
  if (citations.length > 0) {
    citationsHtml = `
      <div class="citations-wrapper">
        <div class="citations-header" onclick="this.nextElementSibling.style.display = this.nextElementSibling.style.display === 'none' ? 'block' : 'none'">
          <span>📚 ${citations.length} Grounded Citations</span>
          <span style="font-size:10px;">(Click to view sources)</span>
        </div>
        <div style="display:none; margin-top:8px;">
          ${citations.map(c => `
            <div class="citation-card">
              <div class="citation-meta">
                <span>📄 ${escapeHtml(c.filename || 'source.txt')}</span>
                <span>Score: ${(c.similarityScore ? c.similarityScore.toFixed(3) : 'High')}</span>
              </div>
              <div class="citation-excerpt">"${escapeHtml(c.content)}"</div>
            </div>
          `).join("")}
        </div>
      </div>
    `;
  }

  row.innerHTML = `
    <div class="message-avatar">⚡</div>
    <div class="message-bubble">
      <div>${escapeHtml(data.answer)}</div>
      
      <div class="meta-pill-bar">
        ${isCacheHit 
          ? `<span class="badge-cache-hit">⚡ CACHE HIT (${elapsedMs}ms)</span>` 
          : `<span class="badge-cache-miss">🧠 LLM GENERATED (${elapsedMs}ms)</span>`
        }
        <span class="latency-tag">Top-K: ${citations.length} chunks</span>
      </div>

      ${citationsHtml}
    </div>
  `;
  container.appendChild(row);
  container.scrollTop = container.scrollHeight;
}

// --- Document Upload & Management ---
function setupDropzone() {
  const dropzone = document.getElementById("dropzone");
  if (!dropzone) return;

  ['dragenter', 'dragover'].forEach(eventName => {
    dropzone.addEventListener(eventName, (e) => {
      e.preventDefault();
      dropzone.classList.add("dragover");
    }, false);
  });

  ['dragleave', 'drop'].forEach(eventName => {
    dropzone.addEventListener(eventName, (e) => {
      e.preventDefault();
      dropzone.classList.remove("dragover");
    }, false);
  });

  dropzone.addEventListener('drop', (e) => {
    const dt = e.dataTransfer;
    const files = dt.files;
    if (files.length > 0) {
      uploadFile(files[0]);
    }
  });
}

function handleFileUpload(e) {
  const file = e.target.files[0];
  if (file) {
    uploadFile(file);
  }
}

async function uploadFile(file) {
  if (!currentToken) {
    showToast("Please sign in first", true);
    checkAuth();
    return;
  }

  showToast(`Uploading ${file.name}...`);
  const formData = new FormData();
  formData.append("file", file);

  try {
    const res = await fetch(`${API_BASE}/documents`, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${currentToken}`
      },
      body: formData
    });

    if (res.status === 401 || res.status === 403) {
      showToast("Session expired, please sign in again", true);
      logout();
      return;
    }

    let data = {};
    try {
      data = await res.json();
    } catch (_) {
      throw new Error(`Upload failed (Server returned HTTP ${res.status})`);
    }

    if (!res.ok) {
      throw new Error(data.message || data.error || data.detail || `Upload rejected (${res.status})`);
    }

    showToast(`Document uploaded (ID: ${data.documentId}). Ingestion started.`);

    const docRecord = {
      id: data.documentId,
      filename: file.name,
      status: data.status || "PENDING",
      chunks: "-",
      time: new Date().toLocaleTimeString()
    };
    uploadedDocs.unshift(docRecord);
    localStorage.setItem("ragforge_docs", JSON.stringify(uploadedDocs));
    renderDocsTable();

    // Poll status until completed
    pollDocStatus(data.documentId);
  } catch (err) {
    showToast(err.message, true);
  }
}

async function pollDocStatus(docId) {
  let attempts = 0;
  const interval = setInterval(async () => {
    attempts++;
    if (attempts > 30) {
      clearInterval(interval);
      return;
    }
    try {
      const res = await fetch(`${API_BASE}/documents/${docId}`, {
        headers: { "Authorization": `Bearer ${currentToken}` }
      });
      if (!res.ok) return;
      const data = await res.json();
      
      const doc = uploadedDocs.find(d => d.id === docId);
      if (doc) {
        doc.status = data.status;
        doc.chunks = data.chunkCount || "-";
        localStorage.setItem("ragforge_docs", JSON.stringify(uploadedDocs));
        renderDocsTable();
      }

      if (data.status === "COMPLETED" || data.status === "FAILED") {
        clearInterval(interval);
        showToast(`Document ${docId} ${data.status.toLowerCase()}!`);
      }
    } catch (e) {
      // ignore transient poll error
    }
  }, 2000);
}

function renderDocsTable() {
  const tbody = document.getElementById("docs-table-body");
  if (!uploadedDocs || uploadedDocs.length === 0) {
    tbody.innerHTML = `<tr><td colspan="5" style="text-align:center; color:var(--text-muted); padding:32px;">No documents uploaded yet. Upload your first document above!</td></tr>`;
    return;
  }

  tbody.innerHTML = uploadedDocs.map(d => `
    <tr>
      <td style="font-family:'JetBrains Mono', monospace; font-size:12px;">#${d.id}</td>
      <td style="font-weight:600;">📄 ${escapeHtml(d.filename)}</td>
      <td>
        <span class="status-badge ${d.status.toLowerCase()}">${escapeHtml(d.status)}</span>
      </td>
      <td>${d.chunks || '-'} chunks</td>
      <td style="color:var(--text-muted); font-size:12px;">${d.time || 'Recent'}</td>
    </tr>
  `).join("");
}

function refreshDocuments() {
  uploadedDocs.forEach(d => {
    if (d.status !== "COMPLETED") {
      pollDocStatus(d.id);
    }
  });
  renderDocsTable();
  showToast("Document list refreshed");
}

// --- Usage & Telemetry Analytics ---
async function fetchTenantUsage() {
  if (!currentToken) return;

  try {
    const res = await fetch(`${API_BASE}/usage`, {
      headers: { "Authorization": `Bearer ${currentToken}` }
    });
    if (!res.ok) throw new Error("Could not load usage data");
    const data = await res.json();

    document.getElementById("stat-total-reqs").innerText = data.totalRequests;
    document.getElementById("stat-cache-hits").innerText = data.totalCacheHits;
    document.getElementById("stat-cache-ratio").innerText = `${(data.cacheHitRate * 100).toFixed(1)}%`;
    document.getElementById("stat-avg-latency").innerText = `${Math.round(data.averageLatencyMs)} ms`;

    const epContainer = document.getElementById("endpoint-breakdown");
    const eps = data.requestsByEndpoint || {};
    if (Object.keys(eps).length === 0) {
      epContainer.innerHTML = "No requests recorded yet.";
    } else {
      epContainer.innerHTML = Object.entries(eps).map(([ep, count]) => `
        <div style="display:flex; justify-content:space-between; padding:8px 0; border-bottom:1px solid rgba(255,255,255,0.05);">
          <span style="font-family:'JetBrains Mono',monospace; color:var(--accent-blue);">${escapeHtml(ep)}</span>
          <span style="font-weight:600;">${count} requests</span>
        </div>
      `).join("");
    }
  } catch (err) {
    showToast(err.message, true);
  }
}

// Utilities
function escapeHtml(str) {
  if (!str) return "";
  return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}
