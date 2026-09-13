(function () {
  const base = '/api';

  async function request(path, options = {}) {
    const response = await fetch(`${base}${path}`, {
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
      ...options
    });
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
    deleteNote: id => request(`/anotacoes/${id}`, { method: 'DELETE' })
  };
})();
