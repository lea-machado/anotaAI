(function () {
  function toast(message, type = 'success') {
    let wrap = document.querySelector('.toast-wrap');
    if (!wrap) {
      wrap = document.createElement('div');
      wrap.className = 'toast-wrap';
      document.body.appendChild(wrap);
    }
    const item = document.createElement('div');
    item.className = `toast ${type}`;
    const marker = document.createElement('span');
    marker.className = 'toast-marker';
    marker.textContent = type === 'success' ? '✓' : '!';
    const text = document.createElement('span');
    text.textContent = message;
    item.append(marker, text);
    wrap.appendChild(item);
    setTimeout(() => item.classList.add('show'), 10);
    setTimeout(() => {
      item.classList.remove('show');
      setTimeout(() => item.remove(), 220);
    }, 2800);
  }

  function openModal(id) {
    const modal = document.getElementById(id);
    if (!modal) return;
    modal.hidden = false;
    document.body.classList.add('modal-open');
    requestAnimationFrame(() => modal.classList.add('is-open'));
  }

  function closeModal(modal) {
    if (!modal) return;
    modal.classList.remove('is-open');
    setTimeout(() => {
      modal.hidden = true;
      document.body.classList.remove('modal-open');
    }, 180);
  }

  document.addEventListener('click', function (event) {
    const actionElement = event.target.closest('[data-action]');
    const action = actionElement ? actionElement.dataset.action : null;

    if (action === 'toggle-menu') {
      document.body.classList.toggle('sidebar-open');
    }
    if (action === 'logout') {
      openModal('logout-modal');
    }
    if (action === 'close-modal') {
      closeModal(event.target.closest('.modal'));
    }
    if (event.target.classList.contains('modal')) {
      closeModal(event.target);
    }
  });

  window.App = { toast, openModal, closeModal };
})();
