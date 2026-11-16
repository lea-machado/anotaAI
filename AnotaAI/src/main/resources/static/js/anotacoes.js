(function () {
  const grid = document.getElementById('notes-grid');
  if (!grid) return;
  const empty = document.getElementById('empty-state');
  const count = document.getElementById('notes-count');
  const searchForm = document.getElementById('notes-search');
  const searchInput = document.getElementById('notes-search-input');
  const clearSearch = document.getElementById('notes-search-clear');
  let activeSearch = '';
  let requestId = 0;
  const openDeleteDialog = createDeleteDialog({
    remove: Api.deleteNote,
    errorMessage: 'Não foi possível excluir a anotação.',
    onDeleted() {
      searchInput.focus();
      loadNotes(activeSearch);
      App.toast('Anotação excluída com sucesso.');
    }
  });

  async function loadNotes(search) {
    activeSearch = search;
    const currentRequest = ++requestId;
    grid.hidden = true;
    empty.hidden = true;
    count.textContent = 'Carregando anotações…';
    try {
      const notes = await Api.listNotes(search);
      if (currentRequest === requestId) render(notes, search);
    } catch (error) {
      if (currentRequest !== requestId) return;
      count.textContent = 'Não foi possível carregar suas anotações.';
      App.toast(error.message || count.textContent, 'error');
    }
  }

  function render(notes, search) {
    grid.hidden = !notes.length;
    empty.hidden = Boolean(search || notes.length);
    count.textContent = search && !notes.length
      ? 'Nenhuma anotação encontrada para esse título.'
      : `${notes.length} ${notes.length === 1 ? 'anotação' : 'anotações'}`;
    grid.replaceChildren();
    for (const note of notes) {
      const id = String(note.id ?? '');
      const titulo = String(note.titulo ?? '');
      const article = document.createElement('article');
      article.className = 'note-card';
      const link = document.createElement('a');
      link.className = 'note-card-link';
      link.href = `nova-anotacao.html?id=${encodeURIComponent(id)}`;
      const top = document.createElement('div');
      top.className = 'note-card-top';
      const heading = document.createElement('h2');
      heading.textContent = titulo;
      const open = document.createElement('span');
      open.className = 'note-open';
      open.textContent = 'Abrir ↗';
      top.append(heading, open);
      const preview = document.createElement('p');
      preview.className = 'note-preview';
      preview.textContent = String(note.conteudo ?? '');
      const meta = document.createElement('div');
      meta.className = 'note-meta';
      const updated = document.createElement('span');
      updated.textContent = `Atualizada em ${String(note.atualizadoEm ?? '')}`;
      meta.append(updated);
      link.append(top, preview, meta);
      const button = document.createElement('button');
      button.className = 'note-delete';
      button.type = 'button';
      button.dataset.deleteNote = id;
      button.dataset.noteTitle = titulo;
      button.setAttribute('aria-label', `Excluir anotação ${titulo}`);
      button.title = 'Excluir anotação';
      const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      svg.setAttribute('aria-hidden', 'true');
      svg.setAttribute('class', 'simple-svg');
      svg.setAttribute('viewBox', '0 0 24 24');
      const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      path.setAttribute('d', 'M4 7h16M9 7V4h6v3M7 7l1 13h8l1-13M10 11v5M14 11v5');
      svg.append(path);
      const label = document.createElement('span');
      label.textContent = 'Excluir';
      button.append(svg, label);
      article.append(link, button);
      grid.append(article);
    }
  }

  grid.addEventListener('click', event => {
    const deleteButton = event.target.closest('[data-delete-note]');
    if (!deleteButton) return;

    event.preventDefault();
    event.stopPropagation();
    openDeleteDialog(Number(deleteButton.dataset.deleteNote), deleteButton.dataset.noteTitle || 'Anotação sem título');
  });

  searchForm.addEventListener('submit', event => {
    event.preventDefault();
    loadNotes(searchInput.value.trim());
  });
  clearSearch.addEventListener('click', () => {
    searchInput.value = '';
    searchInput.focus();
    loadNotes('');
  });

  loadNotes('');
})();
