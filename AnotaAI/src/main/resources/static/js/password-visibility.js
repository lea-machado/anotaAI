document.addEventListener('DOMContentLoaded', () => {
  const eye = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/></svg>';
  const eyeOff = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18M10.6 5.1A10.5 10.5 0 0 1 12 5c6.5 0 10 7 10 7a15.2 15.2 0 0 1-3 3.8M6.1 6.1C3.5 8.1 2 12 2 12s3.5 7 10 7a10.5 10.5 0 0 0 4.1-.8"/><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/></svg>';
  document.querySelectorAll('input[type="password"]').forEach(input => {
    const field = document.createElement('div');
    field.className = 'password-field';
    input.parentNode.insertBefore(field, input);
    field.appendChild(input);
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'password-toggle';
    button.setAttribute('aria-label', 'Mostrar senha');
    button.setAttribute('aria-pressed', 'false');
    button.title = 'Mostrar senha';
    if (input.id) button.setAttribute('aria-controls', input.id);
    button.innerHTML = eye;
    button.addEventListener('click', () => {
      const visible = input.type === 'password';
      input.type = visible ? 'text' : 'password';
      const label = visible ? 'Ocultar senha' : 'Mostrar senha';
      button.setAttribute('aria-label', label);
      button.setAttribute('aria-pressed', String(visible));
      button.title = label;
      button.innerHTML = visible ? eyeOff : eye;
    });
    field.appendChild(button);
  });
});
