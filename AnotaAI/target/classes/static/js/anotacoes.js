(function () {
  const grid = document.getElementById('notes-grid');
  if (!grid) return;
  const empty = document.getElementById('empty-state');
  const count = document.getElementById('notes-count');
  const toggle = document.getElementById('toggle-empty');
  let showEmpty = false;
  let apiNotes = null;

  async function loadNotes() {
    try {
      apiNotes = await Api.listNotes();
    } catch (error) {
      apiNotes = AppMocks.anotacoes;
    }
    render();
  }

  function render() {
    const notes = showEmpty ? [] : (apiNotes || AppMocks.anotacoes);
    grid.hidden = !notes.length;
    empty.hidden = !!notes.length;
    count.textContent = `${notes.length} ${notes.length === 1 ? 'anotação' : 'anotações'}`;
    grid.innerHTML = notes.map(note => `
      <article class="note-card">
        <a class="note-card-link" href="nova-anotacao.html?id=${note.id}">
          <div class="note-card-top"><h2>${note.titulo}</h2><span class="note-open">Abrir ↗</span></div>
          <p class="note-preview">${note.conteudo}</p>
          <div class="note-meta"><span>Atualizada em ${note.atualizadoEm}</span></div>
        </a>
        <button
          class="note-delete"
          type="button"
          data-delete-note="${note.id}"
          data-note-title="${note.titulo}"
          aria-label="Excluir anotação ${note.titulo}"
          title="Excluir anotação"
        >
          <svg aria-hidden="true" class="simple-svg" viewBox="0 0 24 24">
            <path d="M4 7h16M9 7V4h6v3M7 7l1 13h8l1-13M10 11v5M14 11v5"></path>
          </svg>
          <span>Excluir</span>
        </button>
      </article>`).join('');
    toggle.textContent = showEmpty ? 'Voltar às anotações' : 'Visualizar estado vazio';
  }

  async function deleteNote(button) {
    const id = Number(button.dataset.deleteNote);
    const title = button.dataset.noteTitle || 'esta anotação';

    const confirmed = window.confirm(`Deseja realmente excluir a anotação "${title}"?`);
    if (!confirmed) return;

    button.disabled = true;

    try {
      await Api.deleteNote(id);
      apiNotes = (apiNotes || []).filter(note => Number(note.id) !== id);
      render();
      App.toast('Anotação excluída com sucesso.');
    } catch (error) {
      button.disabled = false;
      App.toast(error.message || 'Não foi possível excluir a anotação.', 'error');
    }
  }

  grid.addEventListener('click', event => {
    const deleteButton = event.target.closest('[data-delete-note]');
    if (!deleteButton) return;

    event.preventDefault();
    event.stopPropagation();
    deleteNote(deleteButton);
  });

  toggle.addEventListener('click', () => { showEmpty = !showEmpty; render(); });
  loadNotes();
})();
