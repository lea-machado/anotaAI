(function () {
  const grid = document.getElementById('notes-grid');
  if (!grid) return;
  const empty = document.getElementById('empty-state');
  const count = document.getElementById('notes-count');
  const toggle = document.getElementById('toggle-empty');
  let showEmpty = false;
  let apiNotes = [];

  async function loadNotes() {
    try {
      apiNotes = await Api.listNotes();
    } catch (error) {
      apiNotes = [];
      App.toast(error.message, 'error');
    }
    render();
  }

  function render() {
    const notes = showEmpty ? [] : apiNotes;
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
      </article>`).join('');
    toggle.textContent = showEmpty ? 'Voltar às anotações' : 'Visualizar estado vazio';
  }

  toggle.addEventListener('click', () => { showEmpty = !showEmpty; render(); });
  loadNotes();
})();
