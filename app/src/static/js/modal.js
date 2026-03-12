/**
 * Delete Confirmation Modal
 * Provides a modern, accessible modal dialog for delete confirmations
 */

let deleteCallback = null;

/**
 * Show the delete confirmation modal
 * @param {string} itemName - The name of the item being deleted
 * @param {Function} onConfirm - Callback function to execute on confirmation
 * @param {string} itemDetails - Optional additional details about the item
 */
function showDeleteModal(itemName, onConfirm, itemDetails = null) {
    const modal = document.getElementById('deleteConfirmModal');
    const messageElement = document.getElementById('modalMessage');
    const itemInfoElement = document.getElementById('modalItemInfo');

    // Set the message
    messageElement.textContent = `Are you sure you want to delete "${itemName}"? This action cannot be undone.`;

    // Show item details if provided
    if (itemDetails) {
        itemInfoElement.textContent = itemDetails;
        itemInfoElement.style.display = 'block';
    } else {
        itemInfoElement.style.display = 'none';
    }

    // Store the callback
    deleteCallback = onConfirm;

    // Show the modal
    modal.classList.add('active');

    // Focus the delete button for better UX
    setTimeout(() => {
        const deleteBtn = modal.querySelector('.modal-btn-delete');
        deleteBtn.focus();
    }, 100);

    // Add keyboard event listener for Escape key
    document.addEventListener('keydown', handleModalKeydown);
}

/**
 * Close the delete confirmation modal
 */
function closeDeleteModal() {
    const modal = document.getElementById('deleteConfirmModal');
    modal.classList.remove('active');
    deleteCallback = null;
    document.removeEventListener('keydown', handleModalKeydown);
}

/**
 * Confirm the delete action
 */
function confirmDelete() {
    if (deleteCallback && typeof deleteCallback === 'function') {
        deleteCallback();
    }
    closeDeleteModal();
}

/**
 * Handle keyboard events in the modal
 */
function handleModalKeydown(event) {
    const modal = document.getElementById('deleteConfirmModal');
    if (!modal.classList.contains('active')) {
        return;
    }

    if (event.key === 'Escape') {
        closeDeleteModal();
    } else if (event.key === 'Enter') {
        event.preventDefault();
        confirmDelete();
    }
}

/**
 * Close modal when clicking on the backdrop
 */
document.addEventListener('DOMContentLoaded', () => {
    const modal = document.getElementById('deleteConfirmModal');
    if (modal) {
        modal.addEventListener('click', (event) => {
            if (event.target === modal) {
                closeDeleteModal();
            }
        });
    }
});
