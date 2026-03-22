/***
 * Ethereal Dust Particle System
 * Recreating the fluid, wave-like motion of dust particles.
 */

// --- 1. Fast Simplex Noise Implementation (Self-contained) ---
const FastSimplexNoise = (function () {
  var F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
  var G2 = (3.0 - Math.sqrt(3.0)) / 6.0;
  var F3 = 1.0 / 3.0;
  var G3 = 1.0 / 6.0;

  var P = new Uint8Array([151, 160, 137, 91, 90, 15, 131, 13, 201, 95, 96, 53, 194, 233, 7, 225, 140, 36, 103, 30, 69, 142, 8, 99, 37, 240, 21, 10, 23, 190, 6, 148, 247, 120, 234, 75, 0, 26, 197, 62, 94, 252, 219, 203, 117, 35, 11, 32, 57, 177, 33, 88, 237, 149, 56, 87, 174, 20, 125, 136, 171, 168, 68, 175, 74, 165, 71, 134, 139, 48, 27, 166, 77, 146, 158, 231, 83, 111, 229, 122, 60, 211, 133, 230, 220, 105, 92, 41, 55, 46, 245, 40, 244, 102, 143, 54, 65, 25, 63, 161, 1, 216, 80, 73, 209, 76, 132, 187, 208, 89, 18, 169, 200, 196, 135, 130, 116, 188, 159, 86, 164, 100, 109, 198, 173, 186, 3, 64, 52, 217, 226, 250, 124, 123, 5, 202, 38, 147, 118, 126, 255, 82, 85, 212, 207, 206, 59, 227, 47, 16, 58, 17, 182, 189, 28, 42, 223, 183, 170, 213, 119, 248, 152, 2, 44, 154, 163, 70, 221, 153, 101, 155, 167, 43, 172, 9, 129, 22, 39, 253, 19, 98, 108, 110, 79, 113, 224, 232, 178, 185, 112, 104, 218, 246, 97, 228, 251, 34, 242, 193, 238, 210, 144, 12, 191, 179, 162, 241, 81, 51, 145, 235, 249, 14, 239, 107, 49, 192, 214, 31, 181, 199, 106, 157, 184, 84, 204, 176, 115, 121, 50, 45, 127, 4, 150, 254, 138, 236, 205, 93, 222, 114, 67, 29, 24, 72, 243, 141, 128, 195, 78, 66, 215, 61, 156, 180]);

  var perm = new Uint8Array(512);
  var permMod12 = new Uint8Array(512);
  for (var i = 0; i < 512; i++) {
    perm[i] = P[i & 255];
    permMod12[i] = (perm[i] % 12) * 3;
  }

  function Noise() { }

  Noise.prototype = {
    noise3D: function (xin, yin, zin) {
      var n0, n1, n2, n3;
      var s = (xin + yin + zin) * F3;
      var i = Math.floor(xin + s);
      var j = Math.floor(yin + s);
      var k = Math.floor(zin + s);
      var t = (i + j + k) * G3;
      var X0 = i - t;
      var Y0 = j - t;
      var Z0 = k - t;
      var x0 = xin - X0;
      var y0 = yin - Y0;
      var z0 = zin - Z0;

      var i1, j1, k1;
      var i2, j2, k2;
      if (x0 >= y0) {
        if (y0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
        else if (x0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1; }
        else { i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1; }
      } else {
        if (y0 < z0) { i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1; }
        else if (x0 < z0) { i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1; }
        else { i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
      }

      var x1 = x0 - i1 + G3;
      var y1 = y0 - j1 + G3;
      var z1 = z0 - k1 + G3;
      var x2 = x0 - i2 + 2.0 * G3;
      var y2 = y0 - j2 + 2.0 * G3;
      var z2 = z0 - k2 + 2.0 * G3;
      var x3 = x0 - 1.0 + 3.0 * G3;
      var y3 = y0 - 1.0 + 3.0 * G3;
      var z3 = z0 - 1.0 + 3.0 * G3;

      var ii = i & 255;
      var jj = j & 255;
      var kk = k & 255;

      var gi0 = permMod12[ii + perm[jj + perm[kk]]];
      var gi1 = permMod12[ii + i1 + perm[jj + j1 + perm[kk + k1]]];
      var gi2 = permMod12[ii + i2 + perm[jj + j2 + perm[kk + k2]]];
      var gi3 = permMod12[ii + 1 + perm[jj + 1 + perm[kk + 1]]];

      var t0 = 0.6 - x0 * x0 - y0 * y0 - z0 * z0;
      if (t0 < 0) n0 = 0.0;
      else { t0 *= t0; n0 = t0 * t0 * ((grad3[gi0] * x0) + (grad3[gi0 + 1] * y0) + (grad3[gi0 + 2] * z0)); }

      var t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1;
      if (t1 < 0) n1 = 0.0;
      else { t1 *= t1; n1 = t1 * t1 * ((grad3[gi1] * x1) + (grad3[gi1 + 1] * y1) + (grad3[gi1 + 2] * z1)); }

      var t2 = 0.6 - x2 * x2 - y2 * y2 - z2 * z2;
      if (t2 < 0) n2 = 0.0;
      else { t2 *= t2; n2 = t2 * t2 * ((grad3[gi2] * x2) + (grad3[gi2 + 1] * y2) + (grad3[gi2 + 2] * z2)); }

      var t3 = 0.6 - x3 * x3 - y3 * y3 - z3 * z3;
      if (t3 < 0) n3 = 0.0;
      else { t3 *= t3; n3 = t3 * t3 * ((grad3[gi3] * x3) + (grad3[gi3 + 1] * y3) + (grad3[gi3 + 2] * z3)); }

      return 32.0 * (n0 + n1 + n2 + n3);
    }
  };

  var grad3 = [1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1, 0, 1, 0, 1, -1, 0, 1, 1, 0, -1, -1, 0, -1, 0, 1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1];

  return Noise;
})();

// --- Dust Animation Class ---
class DustAnimation {
  constructor(canvasElement) {
    this.canvas = canvasElement;

    console.log('DustAnimation constructor called');
    console.log('Canvas element:', this.canvas);
    console.log('Canvas offsetWidth:', this.canvas.offsetWidth);
    console.log('Canvas offsetHeight:', this.canvas.offsetHeight);

    // Wait for the canvas to be fully rendered in the DOM
    // The canvas needs to be visible to get accurate dimensions
    if (this.canvas.offsetWidth === 0 || this.canvas.offsetHeight === 0) {
      console.warn('Canvas has zero dimensions, using window size');
      // If canvas isn't rendered yet, use window dimensions
      this.canvas.width = window.innerWidth;
      this.canvas.height = window.innerHeight;
    } else {
      // Get the actual display size of the canvas
      this.canvas.width = this.canvas.offsetWidth;
      this.canvas.height = this.canvas.offsetHeight;
    }

    console.log('Canvas resolution set to:', this.canvas.width, 'x', this.canvas.height);

    this.gl = this.canvas.getContext('webgl', { alpha: false, antialias: false });
    if (!this.gl) {
      console.error('WebGL not supported');
      return;
    }

    console.log('WebGL context created successfully');

    this.noise = new FastSimplexNoise();
    this.particles = [];
    this.time = 0;
    this.mouseX = -1000;
    this.mouseY = -1000;

    this.config = {
      particleCount: 15000,
      baseSpeed: 0.1,
      noiseScale: 0.1,
      timeScale: 0.001,
      visibilityDepth: 50000,
      color: { r: 230, g: 240, b: 255 },
      gravityStrength: 0.015,
      bounceDampening: 2.0,
      mouseRepelRadius: 100,
      mouseRepelStrength: 0.1
    };

    this.setupEventListeners();
    this.setupWebGL();
    this.resize();
    this.init();
  }

  setupEventListeners() {
    window.addEventListener('mousemove', (e) => {
      this.mouseX = e.clientX;
      this.mouseY = e.clientY;
    });

    window.addEventListener('resize', () => this.resize());
  }

  setupWebGL() {
    const vsSource = `
      attribute vec2 aPosition;
      attribute float aSize;
      attribute float aAlpha;
      varying float vAlpha;
      uniform vec2 uResolution;

      void main() {
        vec2 normalized = vec2(
          2.0 * aPosition.x / uResolution.x - 1.0,
          1.0 - 2.0 * aPosition.y / uResolution.y
        );
        gl_Position = vec4(normalized, 0.0, 1.0);
        gl_PointSize = aSize;
        vAlpha = aAlpha;
      }
    `;

    const fsSource = `
      precision mediump float;
      varying float vAlpha;
      uniform vec3 uColor;

      void main() {
        vec2 coord = gl_PointCoord - vec2(0.5, 0.5);
        if (length(coord) > 0.5) discard;
        gl_FragColor = vec4(uColor, vAlpha);
      }
    `;

    const vertexShader = this.gl.createShader(this.gl.VERTEX_SHADER);
    this.gl.shaderSource(vertexShader, vsSource);
    this.gl.compileShader(vertexShader);

    const fragmentShader = this.gl.createShader(this.gl.FRAGMENT_SHADER);
    this.gl.shaderSource(fragmentShader, fsSource);
    this.gl.compileShader(fragmentShader);

    this.program = this.gl.createProgram();
    this.gl.attachShader(this.program, vertexShader);
    this.gl.attachShader(this.program, fragmentShader);
    this.gl.linkProgram(this.program);

    this.aPosition = this.gl.getAttribLocation(this.program, 'aPosition');
    this.aSize = this.gl.getAttribLocation(this.program, 'aSize');
    this.aAlpha = this.gl.getAttribLocation(this.program, 'aAlpha');
    this.uResolution = this.gl.getUniformLocation(this.program, 'uResolution');
    this.uColor = this.gl.getUniformLocation(this.program, 'uColor');

    this.particleBuffer = this.gl.createBuffer();

    this.gl.enable(this.gl.BLEND);
    this.gl.blendFunc(this.gl.SRC_ALPHA, this.gl.ONE_MINUS_SRC_ALPHA);
  }

  resize() {
    // Get the actual display size of the canvas using offsetWidth/offsetHeight
    // These give us the rendered size in the DOM
    this.width = this.canvas.offsetWidth || window.innerWidth;
    this.height = this.canvas.offsetHeight || window.innerHeight;

    // Set the canvas resolution (not just CSS size)
    this.canvas.width = this.width;
    this.canvas.height = this.height;

    this.gl.viewport(0, 0, this.width, this.height);
  }

  init() {
    this.particles = [];
    for (let i = 0; i < this.config.particleCount; i++) {
      this.particles.push(new DustParticle(this.width, this.height));
    }
    this.animate();
  }

  animate = () => {
    requestAnimationFrame(this.animate);
    this.time++;

    this.gl.clearColor(0.0, 0.0, 0.0, 1.0);
    this.gl.clear(this.gl.COLOR_BUFFER_BIT);

    for (let i = 0; i < this.particles.length; i++) {
      this.particles[i].update(
        this.noise,
        this.time,
        this.config,
        this.width,
        this.height,
        this.mouseX,
        this.mouseY
      );
    }

    const data = new Float32Array(this.config.particleCount * 4);
    for (let i = 0; i < this.config.particleCount; i++) {
      const offset = i * 4;
      const p = this.particles[i];
      const flicker = Math.sin(p.life * 0.05) * 0.1;
      data[offset] = p.x;
      data[offset + 1] = p.y;
      data[offset + 2] = p.size;
      data[offset + 3] = Math.max(0.0, Math.min(1.0, p.baseAlpha + flicker));
    }

    this.gl.useProgram(this.program);
    this.gl.uniform2f(this.uResolution, this.width, this.height);
    this.gl.uniform3f(
      this.uColor,
      this.config.color.r / 255.0,
      this.config.color.g / 255.0,
      this.config.color.b / 255.0
    );

    this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.particleBuffer);
    this.gl.bufferData(this.gl.ARRAY_BUFFER, data, this.gl.DYNAMIC_DRAW);

    const stride = 4 * 4;
    this.gl.enableVertexAttribArray(this.aPosition);
    this.gl.vertexAttribPointer(this.aPosition, 2, this.gl.FLOAT, false, stride, 0);

    this.gl.enableVertexAttribArray(this.aSize);
    this.gl.vertexAttribPointer(this.aSize, 1, this.gl.FLOAT, false, stride, 8);

    this.gl.enableVertexAttribArray(this.aAlpha);
    this.gl.vertexAttribPointer(this.aAlpha, 1, this.gl.FLOAT, false, stride, 12);

    this.gl.drawArrays(this.gl.POINTS, 0, this.config.particleCount);
  }
}

// --- Particle Class ---
class DustParticle {
  constructor(width, height) {
    this.width = width;
    this.height = height;
    this.reset(true);
    this.vx = 0;
    this.vy = 0;
  }

  reset(initial = false) {
    this.x = Math.random() * this.width;
    this.y = Math.random() * this.height;
    this.z = Math.random();
    this.size = (1 - this.z) * 0.01 + 0.02 + Math.random() * 0.01;
    this.baseAlpha = (1 - this.z) * 0.7 + 0.3;
    this.life = Math.random() * 1000;
  }

  update(noise, time, config, width, height, mouseX, mouseY) {
    this.width = width;
    this.height = height;

    const n = noise.noise3D(
      this.x * config.noiseScale,
      this.y * config.noiseScale,
      time * config.timeScale
    );

    const angle = n * Math.PI * 4;
    const speed = config.baseSpeed * (1 - this.z * 0.5);

    this.vx += Math.cos(angle) * speed * 0.1;
    this.vy += Math.sin(angle) * speed * 0.1;

    const dx = width - this.x;
    const dy = height - this.y;
    const dist = Math.sqrt(dx * dx + dy * dy) || 1;

    this.vx += (dx / dist) * config.gravityStrength;
    this.vy += (dy / dist) * config.gravityStrength;

    const mx = mouseX - this.x;
    const my = mouseY - this.y;
    const mDist = Math.sqrt(mx * mx + my * my);

    if (mDist < config.mouseRepelRadius && mDist > 0) {
      this.vx -= (mx / mDist) * (config.mouseRepelStrength * (1 - mDist / config.mouseRepelRadius));
      this.vy -= (my / mDist) * (config.mouseRepelStrength * (1 - mDist / config.mouseRepelRadius));
    }

    this.x += this.vx;
    this.y += this.vy;

    if (this.x > width) {
      this.x = width;
      this.vx *= -config.bounceDampening;
    } else if (this.x < 0) {
      this.x = 0;
      this.vx *= -config.bounceDampening;
    }

    if (this.y > height) {
      this.y = height;
      this.vy *= -config.bounceDampening;
    } else if (this.y < 0) {
      this.y = 0;
      this.vy *= -config.bounceDampening;
    }

    this.vx *= 0.98;
    this.vy *= 0.98;

    this.life--;
    if (this.life <= 0) {
      this.reset();
    }
  }
}

// Initialize when DOM is ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initDust);
} else {
  initDust();
}

function initDust() {
  const canvas = document.getElementById('dust-canvas');
  if (canvas) {
    console.log('Dust canvas found, initializing...');
    console.log('Canvas dimensions:', canvas.offsetWidth, canvas.offsetHeight);
    new DustAnimation(canvas);
  } else {
    console.error('Dust canvas not found!');
  }
}
