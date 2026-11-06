(function () {
  const library = document.getElementById('library');
  const study = document.getElementById('study');
  const status = document.getElementById('library-status');
  const grid = document.getElementById('decks-grid');
  const retry = document.getElementById('retry-load');
  let decks = [];
  let activeDeck = null;
  let cardIndex = 0;
  let busy = false;

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
      decks = await Api.listFlashcards();
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
      cardIndex = 0;
      document.getElementById('deck-heading').textContent = activeDeck.titulo;
      renderCard();
      library.hidden = true;
      study.hidden = false;
      document.getElementById('deck-heading').focus();
    } catch (error) { App.toast(error.message, 'error'); }
    finally { busy = false; button.disabled = false; }
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
    answer.textContent = card.resposta;
    answer.hidden = true;
    const reveal = document.getElementById('reveal-answer');
    reveal.textContent = 'Mostrar resposta';
    reveal.setAttribute('aria-expanded', 'false');
    document.getElementById('previous-card').disabled = cardIndex === 0;
    document.getElementById('next-card').disabled = cardIndex === activeDeck.flashcards.length - 1;
  }

  document.getElementById('reveal-answer').addEventListener('click', event => {
    const answer = document.getElementById('card-answer');
    answer.hidden = !answer.hidden;
    event.currentTarget.textContent = answer.hidden ? 'Mostrar resposta' : 'Ocultar resposta';
    event.currentTarget.setAttribute('aria-expanded', String(!answer.hidden));
  });
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
  load();
})();
