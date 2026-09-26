(function () {
  const base = '/api';
  let csrf = null;

  async function getCsrf() {
    if (csrf) return csrf;
    const response = await fetch(`${base}/auth/csrf`, { credentials: 'same-origin' });
    if (!response.ok) throw new Error('Não foi possível iniciar uma sessão segura.');
    csrf = await response.json();
    return csrf;
  }

  async function request(path, options = {}) {
    const method = (options.method || 'GET').toUpperCase();
    const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      const token = await getCsrf();
      headers[token.headerName] = token.token;
    }
    const response = await fetch(`${base}${path}`, {
      credentials: 'same-origin',
      headers,
      ...options
    });
    if (response.status === 401) {
      window.location.href = '/pages/login.html?expirada=1';
      throw new Error('Sua sessão expirou. Entre novamente.');
    }
    if (!response.ok) {
      const error = await response.json().catch(() => ({ mensagem: 'Erro na requisicao.' }));
      throw new Error(error.mensagem || 'Erro na requisicao.');
    }
    if (response.status === 204) return null;
    return response.json();
  }

  window.Api = {
    listNotes: busca => request(`/anotacoes${busca ? `?busca=${encodeURIComponent(busca)}` : ''}`),
    getNote: id => request(`/anotacoes/${id}`),
    createNote: data => request('/anotacoes', { method: 'POST', body: JSON.stringify(data) }),
    updateNote: (id, data) => request(`/anotacoes/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    deleteNote: id => request(`/anotacoes/${id}`, { method: 'DELETE' }),
    getVersions: id => request(`/anotacoes/${id}/versoes`),
    getPerformance: () => request('/desempenho'),
    getCurrentUser: () => request('/auth/me'),
    getAudit: () => request('/auditoria'),
    getAdminSummary: () => request('/admin/resumo'),
    getAdminUsers: () => request('/admin/usuarios'),
    updateAdminUserRole: (id, perfil) => request(`/admin/usuarios/${id}/perfil`, {
      method: 'PUT',
      body: JSON.stringify({ perfil })
    }),
    getAdminAudit: () => request('/admin/auditoria'),
    logout: () => request('/auth/logout', { method: 'POST' })
  };
})();
