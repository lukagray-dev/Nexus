// Additional Interactions

// Horizontal scroll for features section
const featuresScroll = document.querySelector('.features-horizontal-scroll');

if (featuresScroll) {
    // Smooth scroll on wheel
    featuresScroll.addEventListener('wheel', (e) => {
        if (Math.abs(e.deltaY) > Math.abs(e.deltaX)) {
            e.preventDefault();
            featuresScroll.scrollLeft += e.deltaY;
        }
    }, { passive: false });

    // Touch scroll momentum
    let isDown = false;
    let startX;
    let scrollLeft;

    featuresScroll.addEventListener('mousedown', (e) => {
        isDown = true;
        featuresScroll.style.cursor = 'grabbing';
        startX = e.pageX - featuresScroll.offsetLeft;
        scrollLeft = featuresScroll.scrollLeft;
    });

    featuresScroll.addEventListener('mouseleave', () => {
        isDown = false;
        featuresScroll.style.cursor = 'grab';
    });

    featuresScroll.addEventListener('mouseup', () => {
        isDown = false;
        featuresScroll.style.cursor = 'grab';
    });

    featuresScroll.addEventListener('mousemove', (e) => {
        if (!isDown) return;
        e.preventDefault();
        const x = e.pageX - featuresScroll.offsetLeft;
        const walk = (x - startX) * 2;
        featuresScroll.scrollLeft = scrollLeft - walk;
    });

    // Set initial cursor
    featuresScroll.style.cursor = 'grab';
}

// Tilt effect on cards
const cards = document.querySelectorAll('.feature-card, .download-card, .architecture-card');

cards.forEach(card => {
    card.addEventListener('mousemove', (e) => {
        const rect = card.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        
        const centerX = rect.width / 2;
        const centerY = rect.height / 2;
        
        const rotateX = (y - centerY) / 20;
        const rotateY = (centerX - x) / 20;
        
        card.style.transform = `perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) translateZ(10px)`;
    });
    
    card.addEventListener('mouseleave', () => {
        card.style.transform = 'perspective(1000px) rotateX(0) rotateY(0) translateZ(0)';
    });
});

// Number counter animation
const animateCounter = (element, target, duration = 2000) => {
    const start = 0;
    const increment = target / (duration / 16);
    let current = start;
    
    const timer = setInterval(() => {
        current += increment;
        if (current >= target) {
            element.textContent = target;
            clearInterval(timer);
        } else {
            element.textContent = Math.floor(current);
        }
    }, 16);
};

// Observe stat numbers for counter animation
const statObserver = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
        if (entry.isIntersecting && !entry.target.classList.contains('counted')) {
            entry.target.classList.add('counted');
            const text = entry.target.textContent;
            const number = parseInt(text.replace(/\D/g, ''));
            if (!isNaN(number)) {
                entry.target.textContent = '0';
                animateCounter(entry.target, number);
            }
        }
    });
}, { threshold: 0.5 });

document.querySelectorAll('.stat-number').forEach(stat => {
    statObserver.observe(stat);
});

// Smooth reveal for images (when added)
const imageObserver = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
        if (entry.isIntersecting) {
            entry.target.style.opacity = '1';
            entry.target.style.transform = 'scale(1)';
        }
    });
}, { threshold: 0.1 });

document.querySelectorAll('img').forEach(img => {
    img.style.opacity = '0';
    img.style.transform = 'scale(0.95)';
    img.style.transition = 'opacity 0.6s ease, transform 0.6s ease';
    imageObserver.observe(img);
});


// 3D Card Tilt Effect
document.addEventListener('DOMContentLoaded', () => {
    const cards = document.querySelectorAll('.capability-card-3d');
    
    cards.forEach(card => {
        card.addEventListener('mousemove', (e) => {
            const rect = card.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;
            
            const centerX = rect.width / 2;
            const centerY = rect.height / 2;
            
            const rotateX = (y - centerY) / 10;
            const rotateY = (centerX - x) / 10;
            
            card.style.transform = `perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) scale3d(1.02, 1.02, 1.02)`;
        });
        
        card.addEventListener('mouseleave', () => {
            card.style.transform = 'perspective(1000px) rotateX(0) rotateY(0) scale3d(1, 1, 1)';
        });
    });
});


// Pricing Modal
document.addEventListener('DOMContentLoaded', () => {
    const pricingBtn = document.getElementById('pricing-btn');
    const pricingModal = document.getElementById('pricing-modal');
    const pricingClose = document.querySelector('.pricing-close');
    const pricingOverlay = document.querySelector('.pricing-modal-overlay');
    
    // Open modal
    if (pricingBtn) {
        pricingBtn.addEventListener('click', (e) => {
            e.preventDefault();
            pricingModal.classList.add('active');
            document.body.style.overflow = 'hidden';
        });
    }
    
    // Close modal
    const closeModal = () => {
        pricingModal.classList.remove('active');
        document.body.style.overflow = '';
    };
    
    if (pricingClose) {
        pricingClose.addEventListener('click', closeModal);
    }
    
    if (pricingOverlay) {
        pricingOverlay.addEventListener('click', closeModal);
    }
    
    // Close on Escape key
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && pricingModal.classList.contains('active')) {
            closeModal();
        }
    });
    
    // 3D Tilt Effect for Pricing Cards
    const pricingCards = document.querySelectorAll('.pricing-card-3d');
    
    pricingCards.forEach(card => {
        card.addEventListener('mousemove', (e) => {
            const rect = card.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;
            
            const centerX = rect.width / 2;
            const centerY = rect.height / 2;
            
            const rotateX = (y - centerY) / 15;
            const rotateY = (centerX - x) / 15;
            
            card.style.transform = `perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) scale3d(1.02, 1.02, 1.02)`;
        });
        
        card.addEventListener('mouseleave', () => {
            // Preserve scale for featured card
            if (card.classList.contains('pricing-card-featured')) {
                card.style.transform = 'perspective(1000px) rotateX(0) rotateY(0) scale3d(1.05, 1.05, 1.05)';
            } else {
                card.style.transform = 'perspective(1000px) rotateX(0) rotateY(0) scale3d(1, 1, 1)';
            }
        });
    });
});
