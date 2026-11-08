(function () {
  document.querySelectorAll('.sidebar-nav').forEach(nav => {
    if (!nav.querySelector('a[href$="/flashcards.html"], a[href="flashcards.html"]')) {
      const link = document.createElement('a');
      link.className = 'nav-item';
      link.href = '/pages/flashcards.html';
      const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      icon.setAttribute('viewBox', '0 0 24 24');
      icon.setAttribute('class', 'simple-svg');
      icon.setAttribute('aria-hidden', 'true');
      const backCard = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      backCard.setAttribute('d', 'M8 3h12v15');
      const frontCard = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      frontCard.setAttribute('d', 'M4 6h12v15H4zM7 10h6M7 14h6');
      icon.append(backCard, frontCard);
      const label = document.createElement('span');
      label.textContent = 'Meus flashcards';
      link.append(icon, label);
      nav.insertBefore(link, nav.children[1] || null);
    }

    const page = document.body.dataset.page;
    const currentTarget = page === 'flashcards' ? 'flashcards.html'
      : page === 'desempenho' ? 'desempenho.html'
      : page === 'perfil' ? 'perfil.html'
      : page === 'admin' ? 'admin.html'
      : 'anotacoes.html';
    nav.querySelectorAll('a.nav-item[href]').forEach(item => {
      const active = new URL(item.href, location.href).pathname.endsWith(`/${currentTarget}`);
      item.classList.toggle('active', active);
      if (active) item.setAttribute('aria-current', 'page');
      else item.removeAttribute('aria-current');
    });
  });

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
      event.preventDefault();
      Api.logout()
        .catch(error => toast(error.message, 'error'))
        .finally(() => window.location.replace('/pages/login.html'));
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
