(async function () {
  const title = document.getElementById('note-title');
  const content = document.getElementById('note-content');
  const badge = document.getElementById('version-badge');
  const count = document.getElementById('char-count');
  const drawer = document.getElementById('version-drawer');
  const backdrop = document.getElementById('drawer-backdrop');
  const params = new URLSearchParams(location.search);
  let noteId = Number(params.get('id')) || null;
  let originalContent = '';

  if (noteId) {
    try {
      const note = await Api.getNote(noteId);
      title.value = note.titulo;
      content.value = note.conteudo;
      originalContent = note.conteudo;
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
      originalContent = saved.conteudo;
      document.getElementById('save-state').textContent = 'Salva agora';
      App.toast('Anotação salva com sucesso.');
    } catch (error) {
      App.toast(error.message, 'error');
    }
  }

  content.addEventListener('input', updateCount);
  document.getElementById('save-note').addEventListener('click', saveNote);
  document.getElementById('versions').addEventListener('click', openDrawer);
  document.getElementById('close-drawer').addEventListener('click', closeDrawer);
  backdrop.addEventListener('click', closeDrawer);
  document.querySelectorAll('.version-item').forEach(button => {
    button.addEventListener('click', () => selectVersion(button));
  });
  updateCount();

  async function openDrawer() {
    originalContent = content.value;
    if (noteId) {
      try {
        const versions = await Api.getVersions(noteId);
        const firstOriginal = versions.find(version => version.tipo === 'ORIGINAL');
        originalContent = firstOriginal?.conteudo || originalContent;
      } catch (error) {
        App.toast(error.message, 'error');
      }
    }
    backdrop.hidden = false;
    drawer.classList.add('open');
    drawer.setAttribute('aria-hidden', 'false');
    document.getElementById('drawer-preview-text').textContent = originalContent || 'Nenhum conteúdo original disponível.';
  }

  function closeDrawer() {
    drawer.classList.remove('open');
    drawer.setAttribute('aria-hidden', 'true');
    setTimeout(() => {
      backdrop.hidden = true;
    }, 200);
  }

  function selectVersion(button) {
    document.querySelectorAll('.version-item').forEach(item => item.classList.remove('active'));
    button.classList.add('active');
    const preview = document.getElementById('drawer-preview-text');
    if (button.dataset.version === 'original') {
      preview.textContent = originalContent || 'Nenhum conteúdo original disponível.';
      return;
    }
    preview.textContent = 'A versão revisada ainda não foi gerada.';
  }
})();
