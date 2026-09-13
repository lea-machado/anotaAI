(async function () {
  const title = document.getElementById('note-title');
  const content = document.getElementById('note-content');
  const count = document.getElementById('char-count');
  const params = new URLSearchParams(location.search);
  let noteId = Number(params.get('id')) || null;

  if (noteId) {
    try {
      const note = await Api.getNote(noteId);
      title.value = note.titulo;
      content.value = note.conteudo;
    } catch (error) {
      App.toast(error.message, 'error');
    }
  }

  function updateCount() {
    count.textContent = `${content.value.length} caracteres`;
  }

  async function saveNote() {
    if (!title.value.trim() || !content.value.trim()) {
      App.toast('Preencha o título e o conteúdo antes de salvar.', 'error');
      return;
    }

    const payload = {
      titulo: title.value.trim(),
      conteudo: content.value.trim()
    };

    try {
      const saved = noteId ? await Api.updateNote(noteId, payload) : await Api.createNote(payload);
      noteId = saved.id;
      document.getElementById('save-state').textContent = 'Salva agora';
      App.toast('Anotação salva com sucesso.');
    } catch (error) {
      App.toast(error.message, 'error');
    }
  }

  content.addEventListener('input', updateCount);
  document.getElementById('save-note').addEventListener('click', saveNote);
  updateCount();
})();
