document.addEventListener('DOMContentLoaded', function() {
  const hamburger = document.querySelector('.hamburger');
  const links = document.querySelector('.dashboard-links');

  function toggleMenu() {
    links.classList.toggle('open');
    hamburger.setAttribute('aria-expanded', links.classList.contains('open'));
  }

  if (hamburger && links) {
    hamburger.addEventListener('click', toggleMenu);
    hamburger.addEventListener('keydown', function(e) {
      if (e.key === 'Enter' || e.key === ' ') {
        toggleMenu();
      }
    });

    // Close menu when clicking outside
    document.addEventListener('click', function(e) {
      if (!hamburger.contains(e.target) && !links.contains(e.target)) {
        links.classList.remove('open');
        hamburger.setAttribute('aria-expanded', 'false');
      }
    });
  }
});