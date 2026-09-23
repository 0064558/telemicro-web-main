document.addEventListener('DOMContentLoaded', function () {
  // Menu Mobile Toggle
  const menuToggle = document.getElementById('menu-toggle');
  const mobileMenu = document.getElementById('mobile-menu');
  
  if (menuToggle && mobileMenu) {
    menuToggle.addEventListener('click', function () {
      mobileMenu.classList.toggle('active');
      menuToggle.classList.toggle('active');
    });

    // Fechar menu ao clicar em um link
    const menuLinks = mobileMenu.querySelectorAll('a');
    menuLinks.forEach(link => {
      link.addEventListener('click', function () {
        mobileMenu.classList.remove('active');
        menuToggle.classList.remove('active');
      });
    });
  }

  // AOS
  if (window.AOS) {
    AOS.init({ duration: 1200, once: true });
  }

  // Particles
  if (window.particlesJS) {
    particlesJS('particles', {
      particles: {
        number: { value: 80 },
        color: { value: '#00D4FF' },
        shape: { type: 'circle' },
        opacity: { value: 0.5, random: true },
        size: { value: 3, random: true },
        line_linked: { enable: true, distance: 150, color: '#00D4FF', opacity: 0.2, width: 1 },
        move: { enable: true, speed: 2 }
      },
      interactivity: { events: { onhover: { enable: true, mode: 'repulse' } } },
      retina_detect: true
    });
  }

  // CARROSSEL INDESTRUTÍVEL
  const track = document.querySelector('.carousel-track');
  const slides = document.querySelectorAll('.carousel-slide');
  let index = 0;

  const prevBtn = document.querySelector('.carousel-btn.prev');
  const nextBtn = document.querySelector('.carousel-btn.next');

  if (prevBtn && nextBtn && track && slides.length) {
    prevBtn.addEventListener('click', () => moveCarousel(-1));
    nextBtn.addEventListener('click', () => moveCarousel(1));

    function moveCarousel(direction) {
      index = (index + direction + slides.length) % slides.length;
      track.style.transform = `translateX(-${index * 100}%)`;
    }

    // Auto-play
    setInterval(() => moveCarousel(1), 6000);
  }

  // Formulário
  const budgetForm = document.getElementById('budget-form');
  if (budgetForm) {
    budgetForm.addEventListener('submit', function (e) {
      e.preventDefault();
      const form = this;
      const data = new FormData(form);

      const submitButton = form.querySelector('button[type="submit"]');
      if (submitButton) submitButton.disabled = true;

      fetch(form.action, {
        method: 'POST',
        body: data,
        headers: { 'Accept': 'application/json' }
      })
        .then(response => {
          if (!response.ok) throw new Error('Network response was not ok');
          return response.json().catch(() => ({}));
        })
        .then(() => {
          const success = document.getElementById('success');
          if (success) {
            success.classList.add('show');
            success.setAttribute('aria-hidden', 'false');
            form.reset();
            setTimeout(() => {
              success.classList.remove('show');
              success.setAttribute('aria-hidden', 'true');
            }, 4000);
          }
        })
        .catch(() => {
          alert('Erro ao enviar. Verifique sua conexão ou o Formspree.');
        })
        .finally(() => {
          if (submitButton) submitButton.disabled = false;
        });
    });
  }
});
