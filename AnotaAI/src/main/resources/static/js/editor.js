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
  let revisedContent = '';
  let pendingRevision = null;
  let savingNote = false;
  const revisedButton = document.querySelector('[data-version="revisada"]');
  const summaryButton = document.getElementById('generate-summary');
  const cardsButton = document.getElementById('generate-flashcards');
  const generationStatus = document.getElementById('generation-status');
  let generating = false;
  let contentRevision = 0;
  let generatedCards = null;
  let savingCards = false;
  const saveCardsButton = document.getElementById('save-flashcards');
  const cardsSaveStatus = document.getElementById('flashcards-save-status');
  saveCardsButton.addEventListener('click', async () => {
    if (!generatedCards || savingCards || generating) return;
    const deckTitle = document.getElementById('deck-title').value.trim();
    if (!deckTitle) {
      App.toast('Informe um nome para o conjunto de flashcards.', 'error');
      return;
    }
    const cardsToSave = generatedCards;
    savingCards = true;
    saveCardsButton.disabled = summaryButton.disabled = cardsButton.disabled = true;
    cardsSaveStatus.textContent = 'Salvando flashcards…';
    try {
      await Api.saveFlashcards({ titulo: deckTitle, flashcards: cardsToSave });
      if (generatedCards === cardsToSave) generatedCards = null;
      cardsSaveStatus.textContent = 'Salvos! Você pode encontrá-los em Meus flashcards.';
      App.toast('Flashcards salvos na sua conta.');
    } catch (error) {
      cardsSaveStatus.textContent = error.message;
      App.toast(error.message, 'error');
    } finally {
      savingCards = false;
      saveCardsButton.disabled = !generatedCards;
      summaryButton.disabled = cardsButton.disabled = false;
    }
  });

  content.addEventListener('input', () => {
    contentRevision++;
    generatedCards = null;
    document.getElementById('study-results').hidden = true;
    document.getElementById('summary-result').hidden = true;
    document.getElementById('flashcards-result').hidden = true;
  });
  summaryButton.addEventListener('click', () => generate('resumo'));
  cardsButton.addEventListener('click', () => generate('flashcards'));

  async function generate(tipo) {
    if (generating || savingCards || savingNote) return;
    const source = content.value.trim();
    const revision = contentRevision;
    if (!source || source.length > 30000) {
      App.toast('Escreva uma anotação com até 30.000 caracteres para gerar material.', 'error');
      return;
    }
    generating = true;
    summaryButton.disabled = cardsButton.disabled = true;
    saveCardsButton.disabled = true;
    generationStatus.textContent = 'Gerando material com Gemini…';
    generationStatus.dataset.state = 'loading';
    try {
      const result = await Api.generateStudyMaterial(tipo, source);
      if (revision !== contentRevision) {
        generationStatus.textContent = 'O texto mudou durante a geração. Gere novamente para usar a anotação atual.';
        generationStatus.dataset.state = 'info';
        return;
      }
      if (tipo === 'resumo') {
        if (!noteId && !revisedContent) originalContent = content.value;
        revisedContent = result.resumo;
        pendingRevision = result.resumo;
        content.value = result.resumo;
        contentRevision++;
        generatedCards = null;
        document.getElementById('flashcards-result').hidden = true;
        badge.textContent = 'Versão revisada por IA';
        document.getElementById('save-state').textContent = 'Alterações não salvas';
        updateCount();
        refreshVersionPreview();
        document.getElementById('summary-result').hidden = true;
      } else {
        const list = document.getElementById('flashcards-list');
        list.replaceChildren();
        result.flashcards.forEach(card => {
          const item = document.createElement('details');
          const question = document.createElement('summary');
          const answer = document.createElement('p');
          question.textContent = card.pergunta;
          answer.textContent = card.resposta;
          item.append(question, answer);
          list.append(item);
        });
        document.getElementById('flashcards-result').hidden = false;
        generatedCards = result.flashcards;
        document.getElementById('deck-title').value = title.value.trim() || 'Flashcards da anotação';
        cardsSaveStatus.textContent = 'Revise os cartões e salve para estudar depois.';
      }
      document.getElementById('study-results').hidden = tipo === 'resumo';
      (tipo === 'resumo' ? content : document.getElementById('flashcards-result')).scrollIntoView({ behavior: 'smooth', block: 'start' });
      generationStatus.textContent = tipo === 'resumo' ? 'Resumo aplicado à anotação. Original e resumo disponíveis em Versões. Clique em Salvar para manter as alterações.' : 'Flashcards gerados. Clique na pergunta para ver a resposta.';
      generationStatus.dataset.state = 'success';
    } catch (error) {
      generationStatus.textContent = error.message;
      generationStatus.dataset.state = 'error';
      App.toast(error.message, 'error');
    } finally {
      generating = false;
      summaryButton.disabled = cardsButton.disabled = false;
      saveCardsButton.disabled = !generatedCards;
    }
  }

  if (noteId) {
    try {
      const note = await Api.getNote(noteId);
      title.value = note.titulo;
      content.value = note.conteudo;
      originalContent = note.conteudo;
      const versions = await Api.getVersions(noteId);
      originalContent = versions.find(version => version.tipo === 'ORIGINAL')?.conteudo || originalContent;
      revisedContent = versions.filter(version => version.tipo === 'REVISADA_IA').at(-1)?.conteudo || '';
      if (revisedContent && content.value === revisedContent) badge.textContent = 'Versão revisada por IA';
      refreshVersionPreview();
    } catch (error) {
      App.toast(error.message, 'error');
    }
  }

  function updateCount() {
    count.textContent = `${content.value.length} caracteres`;
  }

  async function saveNote() {
    if (savingNote || generating) return;
    if (!title.value.trim() || !content.value.trim()) {
      App.toast('Preencha o título e o conteúdo antes de salvar.', 'error');
      return;
    }

    const payload = {
      titulo: title.value.trim(),
      conteudo: content.value.trim(),
      revisaoIa: pendingRevision,
      conteudoOriginal: originalContent || content.value.trim()
    };

    savingNote = true;
    document.getElementById('save-note').disabled = true;
    try {
      const saved = noteId ? await Api.updateNote(noteId, payload) : await Api.createNote(payload);
      noteId = saved.id;
      if (!originalContent) originalContent = payload.conteudoOriginal;
      if (pendingRevision === payload.revisaoIa) pendingRevision = null;
      history.replaceState(null, '', `${location.pathname}?id=${noteId}`);
      refreshVersionPreview();
      document.getElementById('save-state').textContent = 'Salva agora';
      App.toast('Anotação salva com sucesso.');
    } catch (error) {
      App.toast(error.message, 'error');
    } finally {
      savingNote = false;
      document.getElementById('save-note').disabled = false;
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
    if (!noteId && !revisedContent) originalContent = content.value;
    if (noteId) {
      try {
        const versions = await Api.getVersions(noteId);
        const firstOriginal = versions.find(version => version.tipo === 'ORIGINAL');
        originalContent = firstOriginal?.conteudo || originalContent;
        if (pendingRevision === null) {
          revisedContent = versions.filter(version => version.tipo === 'REVISADA_IA').at(-1)?.conteudo || revisedContent;
        }
      } catch (error) {
        App.toast(error.message, 'error');
      }
    }
    backdrop.hidden = false;
    drawer.classList.add('open');
    drawer.setAttribute('aria-hidden', 'false');
    refreshVersionPreview();
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
    preview.textContent = revisedContent || 'A versão revisada ainda não foi gerada. Clique em Gerar resumo.';
  }

  function refreshVersionPreview() {
    revisedButton.querySelector('small').textContent = revisedContent
      ? (pendingRevision !== null ? 'Gerada · salve para manter no histórico' : 'Salva no histórico')
      : 'Ainda não gerada';
    const selected = document.querySelector('.version-item.active');
    if (selected) selectVersion(selected);
  }
})();
