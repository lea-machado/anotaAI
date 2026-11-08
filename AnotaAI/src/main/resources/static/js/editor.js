(async function () {
  const title = document.getElementById('note-title');
  const content = document.getElementById('note-content');
  const badge = document.getElementById('version-badge');
  const count = document.getElementById('char-count');
  const drawer = document.getElementById('version-drawer');
  const backdrop = document.getElementById('drawer-backdrop');
  const params = new URLSearchParams(location.search);
  let noteId = Number(params.get('id')) || null;
  let savedTitle = null;
  let savedContent = null;
  let originalContent = '';
  let revisedContent = '';
  let pendingRevision = null;
  let pendingFactCheck = null;
  let verificationUnavailable = false;
  let revisionApproved = false;
  let savingNote = false;
  const revisedButton = document.querySelector('[data-version="revisada"]');
  const summaryButton = document.getElementById('generate-summary');
  const cardsButton = document.getElementById('generate-flashcards');
  const summaryChoice = document.getElementById('summary-choice');
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
      await Api.saveFlashcards({ titulo: deckTitle, anotacaoId: noteId, flashcards: cardsToSave });
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
    pendingFactCheck = null;
    verificationUnavailable = false;
    summaryChoice.hidden = true;
    document.getElementById('fact-check-result').hidden = true;
    generatedCards = null;
    const suggestionPending = pendingRevision !== null && !revisionApproved;
    document.getElementById('study-results').hidden = !suggestionPending;
    document.getElementById('summary-result').hidden = !suggestionPending;
    document.getElementById('flashcards-result').hidden = true;
  });
  summaryButton.addEventListener('click', () => generate('resumo'));
  cardsButton.addEventListener('click', () => generate('flashcards'));
  document.getElementById('summarize-original').addEventListener('click', () => {
    if (verificationUnavailable) {
      verificationUnavailable = false;
      summaryChoice.hidden = true;
      document.getElementById('fact-check-result').hidden = true;
      return generate('resumo', { skipFactCheck: true });
    }
    return resumirEscolha(false);
  });
  document.getElementById('summarize-corrected').addEventListener('click', () => resumirEscolha(true));
  document.getElementById('approve-summary').addEventListener('click', () => {
    if (!pendingRevision) return;
    content.value = pendingRevision;
    revisedContent = pendingRevision;
    revisionApproved = true;
    contentRevision++;
    document.getElementById('summary-result').hidden = true;
    document.getElementById('study-results').hidden = true;
    badge.textContent = 'Versão aprovada por você';
    document.getElementById('save-state').textContent = 'Alterações não salvas';
    updateCount();
    refreshVersionPreview();
    App.toast('Sugestão aprovada. Salve para manter a versão organizada.');
  });
  document.getElementById('reject-summary').addEventListener('click', () => {
    pendingRevision = null;
    revisionApproved = false;
    document.getElementById('summary-result').hidden = true;
    document.getElementById('study-results').hidden = true;
    refreshVersionPreview();
    App.toast('Sugestão rejeitada. A anotação original foi preservada.');
  });

  async function generate(tipo, options = {}) {
    if (generating || savingCards || savingNote) return;
    const source = content.value.trim();
    if (!source || source.length > 30000) {
      App.toast('Escreva uma anotação com até 30.000 caracteres para gerar material.', 'error');
      return;
    }
    const revisionBeforeSave = contentRevision;
    const saved = await saveNote();
    if (!saved) return;
    if (contentRevision !== revisionBeforeSave) {
      generationStatus.textContent = 'A anotação mudou enquanto era salva. Salve novamente e solicite a geração mais uma vez.';
      generationStatus.dataset.state = 'info';
      return;
    }
    const revision = contentRevision;
    generating = true;
    summaryButton.disabled = cardsButton.disabled = true;
    saveCardsButton.disabled = true;
    generationStatus.textContent = 'Gerando material com IA…';
    generationStatus.dataset.state = 'loading';
    try {
      if (tipo === 'resumo' && !options.skipFactCheck) {
        let review;
        try {
          generationStatus.textContent = 'Verificando possíveis erros factuais em fontes externas…';
          review = await Api.checkNoteInconsistencies(noteId);
        } catch (checkError) {
          if (revision !== contentRevision) return;
          pendingFactCheck = null;
          verificationUnavailable = true;
          document.getElementById('fact-check-result').classList.add('verification-failed');
          renderFactCheck([], []);
          document.getElementById('fact-check-status').textContent = `${checkError.message} Você pode gerar o resumo com o texto original mesmo assim.`;
          document.getElementById('summarize-corrected').hidden = true;
          document.getElementById('summarize-original').textContent = 'Gerar resumo com meu texto original';
          summaryChoice.hidden = false;
          document.getElementById('fact-check-result').hidden = false;
          document.getElementById('study-results').hidden = false;
          generationStatus.textContent = `${checkError.message} Você pode gerar o resumo com seu texto original usando o botão abaixo.`;
          generationStatus.dataset.state = 'info';
          App.toast(checkError.message, 'error');
          document.getElementById('fact-check-result').scrollIntoView({ behavior: 'smooth', block: 'center' });
          return;
        }
        if (revision !== contentRevision) {
          generationStatus.textContent = 'O texto mudou durante a verificação. Salve e solicite novamente.';
          generationStatus.dataset.state = 'info';
          return;
        }
        const suggestions = review.sugestoes || [];
        verificationUnavailable = false;
        document.getElementById('fact-check-result').classList.remove('verification-failed');
        renderFactCheck(suggestions, review.fontes || []);
        document.getElementById('summarize-corrected').hidden = false;
        document.getElementById('summarize-original').textContent = 'Manter meu texto e gerar resumo';
        if (suggestions.length) {
          pendingFactCheck = { revision, suggestions };
          summaryChoice.hidden = false;
          document.getElementById('fact-check-result').hidden = false;
          document.getElementById('study-results').hidden = false;
          generationStatus.textContent = 'Encontrei informações que podem precisar de correção. Confira as fontes e escolha como gerar o resumo.';
          generationStatus.dataset.state = 'info';
          document.getElementById('fact-check-result').scrollIntoView({ behavior: 'smooth', block: 'start' });
          return;
        }
        pendingFactCheck = null;
        summaryChoice.hidden = true;
        document.getElementById('fact-check-result').hidden = true;
      }
      const quantity = tipo === 'flashcards' ? Number(document.getElementById('flashcard-quantity').value) : null;
      const result = await Api.generateStudyMaterial(tipo, noteId, quantity, options.conteudo);
      if (revision !== contentRevision) {
        generationStatus.textContent = 'O texto mudou durante a geração. Gere novamente para usar a anotação atual.';
        generationStatus.dataset.state = 'info';
        return;
      }
      if (tipo === 'resumo') {
        pendingRevision = result.resumo;
        revisionApproved = false;
        generatedCards = null;
        document.getElementById('flashcards-result').hidden = true;
        document.getElementById('summary-text').textContent = result.resumo;
        document.getElementById('summary-result').hidden = false;
        refreshVersionPreview();
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
      document.getElementById('study-results').hidden = false;
      (tipo === 'resumo' ? content : document.getElementById('flashcards-result')).scrollIntoView({ behavior: 'smooth', block: 'start' });
      generationStatus.textContent = tipo === 'resumo'
        ? `Resumo gerado com base no texto ${options.conteudo ? 'corrigido' : 'original'}. A anotação só será alterada se você aprovar o resumo e salvar.`
        : 'Flashcards gerados. Clique na pergunta para ver a resposta.';
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

  async function resumirEscolha(aplicarCorrecoes) {
    if (!pendingFactCheck || pendingFactCheck.revision !== contentRevision) {
      summaryChoice.hidden = true;
      App.toast('O texto mudou desde a verificação. Gere o resumo novamente para conferir a anotação atual.', 'error');
      return;
    }
    let conteudo = null;
    if (aplicarCorrecoes) {
      conteudo = content.value;
      const substituicoes = [];
      for (const suggestion of pendingFactCheck.suggestions) {
        const trecho = suggestion.trecho;
        const position = conteudo.indexOf(trecho);
        if (!trecho || !suggestion.sugestao || position < 0) {
          App.toast('Não consegui localizar o trecho para aplicar a sugestão. Seu texto continua preservado.', 'error');
          return;
        }
        substituicoes.push({ inicio: position, fim: position + trecho.length, texto: suggestion.sugestao });
      }
      substituicoes.sort((a, b) => b.inicio - a.inicio);
      for (let i = 1; i < substituicoes.length; i++) {
        if (substituicoes[i - 1].inicio < substituicoes[i].fim) {
          App.toast('As sugestões se sobrepõem. Para evitar alterar o trecho errado, escolha manter seu texto original.', 'error');
          return;
        }
      }
      for (const item of substituicoes) {
        conteudo = conteudo.slice(0, item.inicio) + item.texto + conteudo.slice(item.fim);
      }
    }
    pendingFactCheck = null;
    summaryChoice.hidden = true;
    document.getElementById('fact-check-result').hidden = true;
    document.getElementById('fact-check-result').classList.remove('verification-failed');
    await generate('resumo', { skipFactCheck: true, conteudo: conteudo ?? undefined });
  }

  function renderFactCheck(suggestions, sources) {
    const list = document.getElementById('fact-check-list');
    const sourceList = document.getElementById('fact-check-sources');
    const status = document.getElementById('fact-check-status');
    list.replaceChildren();
    sourceList.replaceChildren();
    if (!suggestions.length) {
      status.textContent = 'Não encontrei sugestões de correção apoiadas pelas fontes consultadas.';
    } else {
      status.textContent = 'Estas afirmações podem estar incorretas. Confira as sugestões e as fontes antes de escolher.';
    }
    suggestions.forEach(suggestion => {
      const item = document.createElement('li');
      const excerpt = document.createElement('p');
      excerpt.textContent = `No seu texto: ${suggestion.trecho}`;
      const reason = document.createElement('p');
      reason.textContent = `O que a fonte indica: ${suggestion.motivo}`;
      const correction = document.createElement('p');
      correction.textContent = `Correção sugerida: ${suggestion.sugestao}`;
      item.append(excerpt, reason, correction);
      list.append(item);
    });
    sources.forEach(source => {
      try {
        const url = new URL(source.url);
        if (!['https:', 'http:'].includes(url.protocol)) return;
        const link = document.createElement('a');
        link.href = url.href;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        link.textContent = source.titulo || url.hostname;
        const item = document.createElement('li');
        item.append(link);
        sourceList.append(item);
      } catch (_) { /* Ignora referências sem URL válida. */ }
    });
  }

  if (noteId) {
    try {
      const note = await Api.getNote(noteId);
      title.value = note.titulo;
      content.value = note.conteudo;
      savedTitle = note.titulo;
      savedContent = note.conteudo;
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

    if (noteId && title.value.trim() === savedTitle && content.value.trim() === savedContent && pendingRevision === null) {
      return { id: noteId };
    }

    const payload = {
      titulo: title.value.trim(),
      conteudo: content.value.trim(),
      revisaoIa: revisionApproved ? pendingRevision : null,
      conteudoOriginal: originalContent || content.value.trim()
    };

    savingNote = true;
    document.getElementById('save-note').disabled = true;
    try {
      const saved = noteId ? await Api.updateNote(noteId, payload) : await Api.createNote(payload);
      noteId = saved.id;
      savedTitle = payload.titulo;
      savedContent = payload.conteudo;
      if (!originalContent) originalContent = payload.conteudoOriginal;
      if (revisionApproved && pendingRevision === payload.revisaoIa) {
        pendingRevision = null;
        revisionApproved = false;
      }
      history.replaceState(null, '', `${location.pathname}?id=${noteId}`);
      refreshVersionPreview();
      document.getElementById('save-state').textContent = 'Salva agora';
      App.toast('Anotação salva com sucesso.');
      return saved;
    } catch (error) {
      App.toast(error.message, 'error');
      return null;
    } finally {
      savingNote = false;
      document.getElementById('save-note').disabled = false;
    }
  }

  content.addEventListener('input', updateCount);
  title.addEventListener('input', () => { contentRevision++; });
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
      ? (pendingRevision !== null ? (revisionApproved ? 'Aprovada · salve para manter no histórico' : 'Sugestão pendente de aprovação') : 'Salva no histórico')
      : 'Ainda não gerada';
    const selected = document.querySelector('.version-item.active');
    if (selected) selectVersion(selected);
  }
})();
