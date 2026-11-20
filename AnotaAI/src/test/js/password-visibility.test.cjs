const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const script = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/password-visibility.js'), 'utf8');

class Element {
  constructor(tag) {
    this.tag = tag;
    this.children = [];
    this.parentNode = null;
    this.handlers = {};
    this.attributes = {};
  }

  insertBefore(child, reference) {
    const index = this.children.indexOf(reference);
    assert.notEqual(index, -1);
    this.children.splice(index, 0, child);
    child.parentNode = this;
  }

  appendChild(child) {
    if (child.parentNode) {
      const siblings = child.parentNode.children;
      siblings.splice(siblings.indexOf(child), 1);
    }
    this.children.push(child);
    child.parentNode = this;
  }

  setAttribute(name, value) { this.attributes[name] = value; }
  addEventListener(name, handler) { this.handlers[name] = handler; }
}

test('mostra e oculta todas as senhas preservando campos e valores', () => {
  const container = new Element('div');
  const inputs = Array.from({ length: 7 }, (_, index) => {
    const input = new Element('input');
    input.id = `senha-${index}`;
    input.type = 'password';
    input.value = `segredo-${index}`;
    input.autocomplete = index === 0 ? 'current-password' : 'new-password';
    container.appendChild(input);
    return input;
  });
  let ready;
  vm.runInNewContext(script, {
    document: {
      addEventListener(name, handler) { if (name === 'DOMContentLoaded') ready = handler; },
      querySelectorAll() { return inputs; },
      createElement(tag) { return new Element(tag); }
    }
  });
  ready();
  assert.equal(container.children.length, 7);
  inputs.forEach((input, index) => {
    const wrapper = input.parentNode;
    const button = wrapper.children[1];
    assert.equal(wrapper.className, 'password-field');
    assert.equal(wrapper.children[0], input);
    assert.equal(button.type, 'button');
    assert.equal(button.attributes['aria-label'], 'Mostrar senha');
    assert.equal(button.attributes['aria-controls'], `senha-${index}`);
    button.handlers.click();
    assert.equal(input.type, 'text');
    assert.equal(button.attributes['aria-label'], 'Ocultar senha');
    assert.equal(button.attributes['aria-pressed'], 'true');
    assert.equal(input.value, `segredo-${index}`);
    button.handlers.click();
    assert.equal(input.type, 'password');
    assert.equal(button.attributes['aria-pressed'], 'false');
    assert.equal(input.value, `segredo-${index}`);
  });
});