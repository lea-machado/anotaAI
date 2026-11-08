(function () {
  const library = document.getElementById('library');
  const study = document.getElementById('study');
  const status = document.getElementById('library-status');
  const grid = document.getElementById('decks-grid');
  const retry = document.getElementById('retry-load');
  const query = new URLSearchParams(location.search);
  const sourceNoteId = Number(query.get('anotacaoId')) || null;
  const reviewButton = document.getElementById('review-errors');
  let decks = [];
  let activeDeck = null;
  let cardIndex = 0;
  let busy = false;
  let reviewMode = false;
  const optionsByCard = new Map();
  const answeredCards = new Map();

  function element(tag, text, className) {
    const node = document.createElement(tag);
    node.textContent = text;
    if (className) node.className = className;
    return node;
  }

  async function load() {
    if (busy) return;
    busy = true;
    retry.hidden = true;
    status.textContent = 'Carregando seus flashcards…';
    try {
      decks = await Api.listFlashcards(sourceNoteId);
      render();
    } catch (error) {
      status.textContent = error.message;
      retry.hidden = false;
    } finally { busy = false; }
  }

  function render() {
    grid.replaceChildren();
    status.textContent = `${decks.length} conjunto(s) salvo(s)`;
    document.getElementById('decks-empty').hidden = decks.length > 0;
    decks.forEach(deck => {
      const card = element('article', '', 'deck-card');
      card.append(element('h2', deck.titulo));
      card.append(element('p', `${deck.quantidade} flashcards`));
      const actions = element('div', '', 'study-navigation');
      const open = element('button', 'Estudar', 'button button-primary');
      open.type = 'button';
      open.addEventListener('click', () => openDeck(deck.id, open));
      const remove = element('button', 'Excluir', 'button');
      remove.type = 'button';
      remove.setAttribute('aria-label', `Excluir conjunto ${deck.titulo}`);
      remove.addEventListener('click', () => deleteDeck(deck, remove));
      actions.append(open, remove);
      card.append(actions);
      grid.append(card);
    });
  }

  async function openDeck(id, button) {
    if (busy) return;
    busy = true;
    button.disabled = true;
    try {
      activeDeck = await Api.getFlashcards(id);
      reviewMode = false;
      cardIndex = 0;
      optionsByCard.clear();
      answeredCards.clear();
      document.getElementById('deck-heading').textContent = activeDeck.titulo;
      renderCard();
      library.hidden = true;
      study.hidden = false;
      document.getElementById('deck-heading').focus();
    } catch (error) { App.toast(error.message, 'error'); }
    finally { busy = false; button.disabled = false; }
  }

  async function reviewErrors() {
    if (busy) return;
    busy = true;
    reviewButton.disabled = true;
    try {
      const errors = await Api.reviewFlashcardErrors(sourceNoteId);
      if (!errors.length) {
        App.toast('Não há flashcards pendentes de revisão.');
        return;
      }
      reviewMode = true;
      optionsByCard.clear();
      answeredCards.clear();
      activeDeck = {
        id: null,
        titulo: 'Revisar flashcards marcados como erro',
        flashcards: errors.map(card => ({
          deckId: card.baralhoId,
          indice: card.indice,
          pergunta: card.pergunta,
          resposta: card.resposta,
          erros: card.erros,
          acertos: 0
        }))
      };
      cardIndex = 0;
      document.getElementById('deck-heading').textContent = activeDeck.titulo;
      renderCard();
      library.hidden = true;
      study.hidden = false;
    } catch (error) {
      App.toast(error.message, 'error');
    } finally {
      busy = false;
      reviewButton.disabled = false;
    }
  }

  async function deleteDeck(deck, button) {
    if (busy || !window.confirm(`Excluir o conjunto "${deck.titulo}" e seus flashcards?`)) return;
    busy = true;
    button.disabled = true;
    try {
      await Api.deleteFlashcards(deck.id);
      decks = decks.filter(item => item.id !== deck.id);
      render();
      App.toast('Conjunto excluído.');
    } catch (error) { App.toast(error.message, 'error'); }
    finally { busy = false; button.disabled = false; }
  }

  function renderCard() {
    const card = activeDeck.flashcards[cardIndex];
    document.getElementById('card-progress').textContent = `Cartão ${cardIndex + 1} de ${activeDeck.flashcards.length}`;
    document.getElementById('card-question').textContent = card.pergunta;
    const answer = document.getElementById('card-answer');
    answer.textContent = `Resposta correta: ${card.resposta}`;
    const feedback = document.getElementById('quiz-feedback');
    const options = document.getElementById('quiz-options');
    const choices = getOptions(card, cardIndex);
    const selected = answeredCards.get(card);
    options.replaceChildren();
    answer.hidden = !selected;
    feedback.hidden = !selected;
    feedback.className = `quiz-feedback${selected?.correct ? ' is-correct' : ' is-incorrect'}`;
    feedback.textContent = selected ? (selected.correct ? 'Correto! Muito bem.' : 'Essa não é a resposta. Confira a correta abaixo.') : '';
    choices.forEach(choice => {
      const button = element('button', choice, 'button quiz-option');
      button.type = 'button';
      button.disabled = Boolean(selected);
      if (selected && choice === card.resposta) button.classList.add('is-correct');
      if (selected && choice === selected.choice && !selected.correct) button.classList.add('is-incorrect');
      button.addEventListener('click', () => answerQuestion(choice));
      options.append(button);
    });
    document.getElementById('card-score').textContent = `Acertos: ${card.acertos || 0} · Erros: ${card.erros || 0}`;
    document.getElementById('previous-card').disabled = cardIndex === 0;
    document.getElementById('next-card').disabled = cardIndex === activeDeck.flashcards.length - 1;
  }

  function getOptions(card, index) {
    if (optionsByCard.has(card)) return optionsByCard.get(card);
    const correct = card.resposta;
    const normalize = value => value.trim().toLocaleLowerCase('pt-BR');
    const distractors = [...new Set(activeDeck.flashcards
      .filter((other, otherIndex) => otherIndex !== index)
      .map(other => other.resposta.trim())
      .filter(value => value && normalize(value) !== normalize(correct)))];
    for (const filler of ['Nenhuma das outras alternativas', 'Todas as alternativas estão incorretas', 'Não é possível determinar']) {
      if (distractors.length >= 3) break;
      if (normalize(filler) !== normalize(correct) && !distractors.some(value => normalize(value) === normalize(filler))) distractors.push(filler);
    }
    const choices = [correct, ...distractors.slice(0, 3)];
    for (let i = choices.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [choices[i], choices[j]] = [choices[j], choices[i]];
    }
    optionsByCard.set(card, choices);
    return choices;
  }

  async function answerQuestion(choice) {
    if (busy) return;
    const card = activeDeck.flashcards[cardIndex];
    if (answeredCards.has(card)) return;
    const correct = choice === card.resposta;
    answeredCards.set(card, { choice, correct });
    renderCard();
    busy = true;
    try {
      const deckId = card.deckId || activeDeck.id;
      const index = Number.isInteger(card.indice) ? card.indice : cardIndex;
      const updated = await Api.registerFlashcardResult(deckId, index, correct);
      const updatedCard = updated.flashcards.find(item => item.indice === index);
      if (updatedCard) {
        card.acertos = updatedCard.acertos;
        card.erros = updatedCard.erros;
      }
      App.toast(correct ? 'Acerto registrado.' : 'Erro registrado.');
      if (reviewMode && correct) {
        activeDeck.flashcards.splice(cardIndex, 1);
        if (!activeDeck.flashcards.length) {
          study.hidden = true;
          library.hidden = false;
          reviewMode = false;
          App.toast('Revisão concluída.');
        } else {
          if (cardIndex >= activeDeck.flashcards.length) cardIndex--;
          renderCard();
        }
      } else {
        document.getElementById('card-score').textContent = `Acertos: ${card.acertos || 0} · Erros: ${card.erros || 0}`;
      }
    } catch (error) {
      answeredCards.delete(card);
      renderCard();
      App.toast(error.message, 'error');
    } finally {
      busy = false;
    }
  }
  document.getElementById('previous-card').addEventListener('click', () => {
    if (cardIndex > 0) { cardIndex--; renderCard(); }
  });
  document.getElementById('next-card').addEventListener('click', () => {
    if (cardIndex < activeDeck.flashcards.length - 1) { cardIndex++; renderCard(); }
  });
  document.getElementById('back-library').addEventListener('click', () => {
    study.hidden = true;
    library.hidden = false;
    activeDeck = null;
  });
  retry.addEventListener('click', load);
  reviewButton.addEventListener('click', reviewErrors);
  load();
})();
