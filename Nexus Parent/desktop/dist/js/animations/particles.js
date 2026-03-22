class SubtleParticles {

/**
 * Constructor for SubtleParticles.
 * @param {string} canvasId - The id of the <canvas> element to render the particles on.
 * @returns {undefined} - Returns nothing.
 * @example
 * const particles = new SubtleParticles('particles-canvas');
 */
    
/**
 * The constructor function initializes a particle system on a canvas element with specified
 * configurations and mouse interactions.
 * @param canvasId - The `canvasId` parameter is the id of the HTML canvas element that you want to use
 * for rendering the particles. This constructor function initializes the properties and configuration
 * settings for a particle system that will be drawn on the specified canvas.
 * @returns If the `document.getElementById(canvasId)` call does not find an element with the specified
 * `canvasId`, `null` will be returned.
 */
constructor(canvasId) {
        this.canvas = document.getElementById(canvasId);
        if (!this.canvas) return;

        this.ctx = this.canvas.getContext('2d');
        this.particles = [];
        this.lastTime = performance.now();

        this.config = {
            density: 9000,                    // lower = more particles
            maxSpeed: 0.25,                  // max particle velocity in px/ms   
            size: [0.8, 1.8],               // particle size range in px
            linkDistance: 140,             // max distance to draw links between particles
            mouseRadius: 160,             // radius of mouse interaction
            mouseForce: 0.04,            // strength of mouse interaction
            friction: 0.985             // velocity friction per frame
        };

        this.mouse = { x: null, y: null };
        this.init();
    }

    /**
     * The `init` function in JavaScript resizes, spawns, binds events, and initiates animation using
     * requestAnimationFrame.
     */
    init() {
        this.resize();                    // set canvas size
        this.spawn();                    // create particles
        this.bind();                    // bind events
        requestAnimationFrame(t => this.animate(t));
    }

    /**
     * The `bind` function sets up event listeners for window resize, canvas mousemove, and canvas
     * mouseleave events.
     */
    bind() {
        window.addEventListener('resize', () => {
            this.resize();
            this.spawn();
        });

        this.canvas.addEventListener('mousemove', e => {
            const r = this.canvas.getBoundingClientRect();
            this.mouse.x = e.clientX - r.left;
            this.mouse.y = e.clientY - r.top;
        });

        this.canvas.addEventListener('mouseleave', () => {
            this.mouse.x = this.mouse.y = null;
        });
    }

    /**
     * The `resize` function adjusts the width and height of a canvas element to match the window
     * dimensions.
     */
    resize() {
        this.canvas.width = window.innerWidth;
        this.canvas.height = window.innerHeight;
    }

    /**
     * The `spawn` function generates an array of particles with random positions, velocities, sizes,
     * and phases based on the canvas dimensions and configuration settings.
     */
    spawn() {
        const count = Math.floor(
            (this.canvas.width * this.canvas.height) / this.config.density
        );

        this.particles = Array.from({ length: count }, () => ({
            x: Math.random() * this.canvas.width,                                // random x position
            y: Math.random() * this.canvas.height,                              // random y position
            vx: (Math.random() - 0.5) * this.config.maxSpeed,                  // random x velocity
            vy: (Math.random() - 0.5) * this.config.maxSpeed,                 // random y velocity
            r: this.rand(this.config.size),                                  // random radius between size range
            phase: Math.random() * Math.PI * 2                              // random phase for organic drift
        }));
    }

    /**
     * The `rand` function generates a random number within a specified range [a, b].
     * @returns The function `rand([a, b])` returns a random number between `a` and `b`.
     */
    rand([a, b]) {
        return a + Math.random() * (b - a);
    }

    /**
     * The `update` function updates the position and velocity of a particle based on the delta time
     * (`dt`) and the current mouse position.
     *
     * It applies a slow organic drift to the particle velocity, a spring force towards the
     * mouse position if the particle is within the mouse radius, and friction to slow down
     * the particle velocity over time.
     *
     * It also wraps the particle position around the canvas edges instead of bouncing.
     *
     * @param {Object} p - A particle object with properties `x`, `y`, `vx`, `vy`, and `phase`.
     * @param {Number} dt - The delta time (in seconds) since the last frame.
     */    
    
    update(p, dt) {
        // slow organic drift
        p.phase += dt * 0.0005;
        p.vx += Math.cos(p.phase) * 0.0006;                  // organic drift
        p.vy += Math.sin(p.phase) * 0.0006;                 // organic drift

        // mouse spring force
        if (this.mouse.x !== null) {
            const dx = p.x - this.mouse.x;                  // distance x to mouse
            const dy = p.y - this.mouse.y;                 // distance y to mouse
            const d = Math.hypot(dx, dy);                 // euclidean distance to mouse

            if (d < this.config.mouseRadius) {
                const f = (1 - d / this.config.mouseRadius) * this.config.mouseForce;
                p.vx += (dx / d) * f;                 // apply force proportional to distance
                p.vy += (dy / d) * f;                // apply force proportional to distance
            }
        }

        p.vx *= this.config.friction;              // apply friction
        p.vy *= this.config.friction;             // apply friction

        p.x += p.vx * dt;
        p.y += p.vy * dt;

        // wrap instead of bounce
        if (p.x < 0) p.x += this.canvas.width;                              // wrap around left edge
        if (p.y < 0) p.y += this.canvas.height;                            // wrap around top edge
        if (p.x > this.canvas.width) p.x -= this.canvas.width;            // wrap around right edge
        if (p.y > this.canvas.height) p.y -= this.canvas.height;         // wrap around bottom edge
    }

    /**
     * Draws a particle at the given position with the given radius.
     * The particle is drawn as a radial gradient circle with a
     * soft white center and a transparent outer edge.
     * @param {Object} p - A particle object with properties `x`, `y`, and `r`.
     */
    drawParticle(p) {
        const g = this.ctx.createRadialGradient(
            p.x, p.y, 0,                                // center
            p.x, p.y, p.r * 4                          // outer edge
        );
        g.addColorStop(0, 'rgba(255,255,255,0.35)');     // soft white center
        g.addColorStop(1, 'rgba(255,255,255,0)');       // transparent edge

        this.ctx.fillStyle = g;                               // set fill style to gradient
        this.ctx.beginPath();                                // start path
        this.ctx.arc(p.x, p.y, p.r * 4, 0, Math.PI * 2);    // draw circle
        this.ctx.fill();                                   // fill circle
    }

    /**
     * Draws lines between particles if they are within a certain distance.
     * The line color and opacity are determined by the distance between the particles.
     * The line width is fixed at 0.6.
     * @see ParticlesBackground#config
     */
    drawLinks() {
        for (let i = 0; i < this.particles.length; i++) {
            for (let j = i + 1; j < this.particles.length; j++) {
                const a = this.particles[i];
                const b = this.particles[j];

                const dx = a.x - b.x;
                const dy = a.y - b.y;
                const d = Math.hypot(dx, dy);

                if (d < this.config.linkDistance) {
                    this.ctx.strokeStyle =
                        `rgba(255,255,255,${(1 - d / this.config.linkDistance) * 0.08})`;
                    this.ctx.lineWidth = 0.6;                // fixed line width
                    this.ctx.beginPath();                   // start path
                    this.ctx.moveTo(a.x, a.y);             // move to particle a 
                    this.ctx.lineTo(b.x, b.y);            // line to particle b
                    this.ctx.stroke();                   // draw line
                }
            }
        }
    }

/**
 * The `animate` function is the main animation loop of the particle
 * background. It is called recursively using `requestAnimationFrame`.
 * It updates the position and velocity of each particle and redraws
 * the entire canvas based on the delta time (`dt`) since the last
 * frame.
 * @param {number} time - The current time in milliseconds since the
 *   page was loaded.
 * @see https://developer.mozilla.org/en-US/docs/Web/API/window/requestAnimationFrame
 */
    animate(time) {
        const dt = Math.min(time - this.lastTime, 32);
        this.lastTime = time;
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);

        for (const p of this.particles) {
            this.update(p, dt);
            this.drawParticle(p);
        }

        this.drawLinks();
        requestAnimationFrame(t => this.animate(t));
    }
}

/* The code `document.addEventListener('DOMContentLoaded', () => { new
SubtleParticles('particles-canvas'); });` is adding an event listener to the `DOMContentLoaded`
event of the `document` object. */
document.addEventListener('DOMContentLoaded', () => {
    new SubtleParticles('particles-canvas');
});
