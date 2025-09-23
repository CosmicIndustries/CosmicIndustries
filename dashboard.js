document.addEventListener('DOMContentLoaded', () => {
  const hamburger = document.querySelector('.hamburger');
  const links = document.querySelector('.dashboard-links');

  function toggleMenu() {
    if (links) {
      links.classList.toggle('open');
      hamburger.setAttribute('aria-expanded', links.classList.contains('open'));
    }
  }

  if (hamburger && links) {
    hamburger.addEventListener('click', toggleMenu);
    hamburger.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        toggleMenu();
      }
    });

    // Close menu when clicking outside
    document.addEventListener('click', (e) => {
      if (
        links.classList.contains('open') &&
        !hamburger.contains(e.target) &&
        !links.contains(e.target)
      ) {
        links.classList.remove('open');
        hamburger.setAttribute('aria-expanded', 'false');
      }
    });
  }
});
