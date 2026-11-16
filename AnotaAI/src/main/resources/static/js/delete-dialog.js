function createDeleteDialog({ remove, onDeleted, errorMessage }) {
  const dialog = document.getElementById('delete-dialog');
  const name = document.getElementById('delete-dialog-name');
  const errorFeedback = document.getElementById('delete-dialog-error');
  const cancel = document.getElementById('delete-dialog-cancel');
  const confirm = document.getElementById('delete-dialog-confirm');
  const confirmLabel = confirm.textContent;
  let itemId;

  cancel.addEventListener('click', () => dialog.close());
  dialog.addEventListener('cancel', event => {
    if (confirm.disabled) event.preventDefault();
  });
  confirm.addEventListener('click', async () => {
    if (confirm.disabled || !dialog.open) return;
    const id = itemId;
    confirm.disabled = true;
    cancel.disabled = true;
    confirm.textContent = 'Excluindo…';
    errorFeedback.textContent = '';
    try {
      await remove(id);
      dialog.close();
    } catch (error) {
      errorFeedback.textContent = error.message || errorMessage;
      return;
    } finally {
      confirm.disabled = false;
      cancel.disabled = false;
      confirm.textContent = confirmLabel;
      if (dialog.open) confirm.focus();
    }
    onDeleted(id);
  });

  return (id, title) => {
    if (dialog.open) return;
    itemId = id;
    name.textContent = title;
    errorFeedback.textContent = '';
    dialog.showModal();
  };
}
