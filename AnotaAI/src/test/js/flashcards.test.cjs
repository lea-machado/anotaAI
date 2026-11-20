const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { setImmediate } = require('node:timers/promises');

const script = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/flashcards.js'), 'utf8');
const dialog = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/delete-dialog.js'), 'utf8');

async function quiz(cards, deleteFlashcards) {
  function element() {
    return {
      children: [],
      handlers: {},
      classList: { add() {} },
      append(...children) { this.children.push(...children); },
      replaceChildren() { this.children = []; },
      setAttribute() {},
      focus() {},
      showModal() { this.open = true; },
      close() { this.open = false; },
      addEventListener(event, handler) { this.handlers[event] = handler; },
      click() { return this.handlers.click(); }
    };
  }
  const elements = new Map();
  const get = id => {
    if (!elements.has(id)) elements.set(id, element());
    return elements.get(id);
  };
  const deck = { id: 1, titulo: 'Baralho', quantidade: cards.length, cartoesComErro: cards.length };
  const errors = [];
  vm.runInNewContext(dialog + '\n' + script, {
    document: { getElementById: get, createElement: element },
    location: { search: '' },
    URLSearchParams,
    Api: {
      async listFlashcards() { return [deck]; },
      async getFlashcards() { return { ...deck, flashcards: cards }; },
      async reviewFlashcardErrors() { return cards; },
      deleteFlashcards
    },
    App: { toast(message) { errors.push(message); } }
  });
  await setImmediate();
  return {
    open: () => get('decks-grid').children[0].children[3].children[0].click(),
    remove: () => get('decks-grid').children[0].children[3].children[1].click(),
    get,
    review: () => get('error-deck-list').children[0].click(),
    next: () => get('next-card').click(),
    previous: () => get('previous-card').click(),
    options: () => get('quiz-options').children.map(option => option.textContent),
    errors
  };
}

test('exclui o conjunto selecionado somente após confirmar', async () => {
  const deleted = [];
  const session = await quiz([], async id => { deleted.push(id); });
  const dialog = session.get('delete-dialog');

  session.remove();
  assert.equal(dialog.open, true);
  assert.equal(session.get('delete-dialog-name').textContent, 'Baralho');
  session.get('delete-dialog-cancel').click();
  assert.equal(dialog.open, false);
  assert.deepEqual(deleted, []);

  session.remove();
  await session.get('delete-dialog-confirm').click();
  assert.deepEqual(deleted, [1]);
  assert.equal(dialog.open, false);
  assert.equal(session.get('decks-grid').children.length, 0);
  assert.equal(session.get('decks-empty').hidden, false);
});

test('usa as alternativas da própria pergunta no estudo e na revisão de erros', async () => {
  const cards = [
    { pergunta: 'Primeira', resposta: 'R1', distratores: ['A1', 'B1', 'C1'] },
    { pergunta: 'Segunda', resposta: 'R2', distratores: ['A2', 'B2', 'C2'] }
  ];
  const session = await quiz(cards);
  for (const open of [session.open, session.review]) {
    await open();
    const firstOptions = session.options();
    assert.deepEqual(firstOptions.toSorted(), ['A1', 'B1', 'C1', 'R1']);
    session.next();
    assert.deepEqual(session.options().toSorted(), ['A2', 'B2', 'C2', 'R2']);
    session.previous();
    assert.deepEqual(session.options(), firstOptions);
  }
  assert.deepEqual(session.errors, []);
});

test('distribui alternativas entre os cartões de baralhos antigos', async () => {
  const cards = Array.from({ length: 7 }, (_, index) => ({ pergunta: 'Pergunta', resposta: `R${index}` }));
  const session = await quiz(cards);
  await session.open();
  const wrongFirst = session.options().filter(option => option !== 'R0');
  assert.equal(wrongFirst.length, 3);
  session.next();
  const wrongSecond = session.options().filter(option => option !== 'R1');
  assert.equal(wrongSecond.length, 3);
  assert.ok(wrongSecond.every(option => !wrongFirst.includes(option)));
  assert.deepEqual(session.errors, []);
});