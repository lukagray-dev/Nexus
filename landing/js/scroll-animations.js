// GSAP Scroll Animations

// Register ScrollTrigger plugin
gsap.registerPlugin(ScrollTrigger);

// Wait for DOM to be ready
document.addEventListener('DOMContentLoaded', () => {

    // Intro Section Animations
    gsap.from('.intro-content .badge', {
        opacity: 0,
        y: 30,
        duration: 1,
        delay: 0.3
    });

    gsap.from('.intro-title', {
        opacity: 0,
        y: 50,
        duration: 1,
        delay: 0.5
    });

    gsap.from('.intro-subtitle', {
        opacity: 0,
        y: 30,
        duration: 1,
        delay: 0.7
    });

    gsap.from('.intro-cta', {
        opacity: 0,
        y: 30,
        duration: 1,
        delay: 0.9
    });

    // Stats Section
    gsap.from('.stat-item', {
        scrollTrigger: {
            trigger: '.stats-section-alt',
            start: 'top 80%',
        },
        opacity: 0,
        y: 50,
        stagger: 0.2,
        duration: 0.8
    });

    // Feature Cards - Stable Infinite Horizontal Scroll
    const featuresScroll = document.querySelector('.features-horizontal-scroll');
    if (featuresScroll) {
        // Clone the feature cards multiple times for seamless infinite scroll
        const originalCards = Array.from(featuresScroll.children);

        // Clone 3 times to ensure smooth infinite scroll
        for (let i = 0; i < 3; i++) {
            originalCards.forEach(card => {
                const clone = card.cloneNode(true);
                featuresScroll.appendChild(clone);
            });
        }

        // Calculate total width
        const cardWidth = 350 + 48; // card width + gap
        const totalCards = originalCards.length;
        const scrollDistance = cardWidth * totalCards;

        // Create smooth infinite scroll animation
        gsap.to('.features-horizontal-scroll', {
            x: -scrollDistance,
            duration: 30,
            ease: 'none',
            repeat: -1,
            modifiers: {
                x: gsap.utils.unitize(x => parseFloat(x) % scrollDistance)
            }
        });
    }

    // Architecture Cards
    gsap.from('.architecture-card', {
        scrollTrigger: {
            trigger: '.architecture-grid',
            start: 'top 80%',
        },
        opacity: 0,
        x: -50,
        stagger: 0.2,
        duration: 0.8
    });

    // Tech Section
    gsap.from('.tech-text', {
        scrollTrigger: {
            trigger: '.tech-section',
            start: 'top 70%',
        },
        opacity: 0,
        x: -50,
        duration: 1
    });

    gsap.from('.tech-stack', {
        scrollTrigger: {
            trigger: '.tech-section',
            start: 'top 70%',
        },
        opacity: 0,
        x: 50,
        duration: 1
    });

    // Capabilities
    gsap.from('.capability-item', {
        scrollTrigger: {
            trigger: '.capabilities-grid',
            start: 'top 80%',
        },
        opacity: 0,
        scale: 0.8,
        stagger: 0.1,
        duration: 0.6
    });

    // Use Cases
    gsap.from('.use-case-card', {
        scrollTrigger: {
            trigger: '.use-cases-grid',
            start: 'top 80%',
        },
        opacity: 0,
        y: 50,
        stagger: 0.15,
        duration: 0.8
    });

    // FAQ Items
    gsap.from('.faq-item', {
        scrollTrigger: {
            trigger: '.faq-grid',
            start: 'top 80%',
        },
        opacity: 0,
        x: -30,
        stagger: 0.1,
        duration: 0.6
    });

    // Download Cards
    gsap.from('.download-card', {
        scrollTrigger: {
            trigger: '.download-grid',
            start: 'top 80%',
        },
        opacity: 0,
        y: 50,
        stagger: 0.2,
        duration: 0.8
    });

    // Setup Steps - Unfold on scroll with phone screen sync
    const setupSteps = document.querySelectorAll('.setup-step');
    const phoneContents = document.querySelectorAll('.phone-content');

    setupSteps.forEach((step, index) => {
        ScrollTrigger.create({
            trigger: step,
            start: 'top center',
            end: 'bottom center',
            onEnter: () => {
                // Remove active class from all phone contents
                phoneContents.forEach(content => content.classList.remove('active'));
                // Add active class to corresponding phone content
                const stepNumber = step.getAttribute('data-step');
                const phoneContent = document.querySelector(`.phone-content[data-screen="${stepNumber}"]`);
                if (phoneContent) {
                    phoneContent.classList.add('active');
                }
            },
            onEnterBack: () => {
                // Remove active class from all phone contents
                phoneContents.forEach(content => content.classList.remove('active'));
                // Add active class to corresponding phone content
                const stepNumber = step.getAttribute('data-step');
                const phoneContent = document.querySelector(`.phone-content[data-screen="${stepNumber}"]`);
                if (phoneContent) {
                    phoneContent.classList.add('active');
                }
            }
        });

        // Animate step in
        gsap.from(step, {
            scrollTrigger: {
                trigger: step,
                start: 'top 85%',
                toggleActions: 'play none none none'
            },
            opacity: 0,
            x: -50,
            duration: 0.6,
            delay: index * 0.1
        });
    });

    // Section Headers
    gsap.utils.toArray('.section-header').forEach(header => {
        gsap.from(header, {
            scrollTrigger: {
                trigger: header,
                start: 'top 85%',
            },
            opacity: 0,
            y: 30,
            duration: 0.8
        });
    });

    // Parallax effect for intro
    gsap.to('.intro-content', {
        scrollTrigger: {
            trigger: '.intro-section',
            start: 'top top',
            end: 'bottom top',
            scrub: true
        },
        y: 100,
        opacity: 0.5
    });

});


    // Parent Dashboard Setup Steps - Unfold on scroll with desktop screen sync
    const parentSteps = document.querySelectorAll('.setup-section-reverse .setup-step');
    const desktopContents = document.querySelectorAll('.desktop-content');
    
    parentSteps.forEach((step, index) => {
        ScrollTrigger.create({
            trigger: step,
            start: 'top center',
            end: 'bottom center',
            onEnter: () => {
                // Remove active class from all desktop contents
                desktopContents.forEach(content => content.classList.remove('active'));
                // Add active class to corresponding desktop content
                const stepNumber = step.getAttribute('data-step');
                const desktopContent = document.querySelector(`.desktop-content[data-screen="${stepNumber}"]`);
                if (desktopContent) {
                    desktopContent.classList.add('active');
                }
            },
            onEnterBack: () => {
                // Remove active class from all desktop contents
                desktopContents.forEach(content => content.classList.remove('active'));
                // Add active class to corresponding desktop content
                const stepNumber = step.getAttribute('data-step');
                const desktopContent = document.querySelector(`.desktop-content[data-screen="${stepNumber}"]`);
                if (desktopContent) {
                    desktopContent.classList.add('active');
                }
            }
        });
        
        // Animate step in from right (lighter animation)
        gsap.from(step, {
            scrollTrigger: {
                trigger: step,
                start: 'top 85%',
                toggleActions: 'play none none none'
            },
            opacity: 0.3,
            x: 30,
            duration: 0.5,
            delay: index * 0.08
        });
    });
