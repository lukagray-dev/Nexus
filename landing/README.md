# Nexus Landing Page

Modern, Apple-style landing page for the Nexus parental control system. Features 3D animations, smooth scroll effects, and a black & gold luxury theme.

## Features

- **3D Particle System**: Interactive Three.js particle background in the hero section
- **Scroll Animations**: GSAP-powered smooth scroll-triggered animations
- **Responsive Design**: Fully responsive across all devices
- **Black & Gold Theme**: Luxury color scheme matching the Nexus brand
- **Comprehensive Content**: 
  - Feature showcase with detailed descriptions
  - Architecture explanation
  - Technical specifications
  - Use cases and FAQ
  - Download section with platform options

## Structure

```
docs/
├── index.html              # Main HTML file
├── css/
│   ├── reset.css          # CSS reset
│   ├── variables.css      # CSS custom properties (colors, spacing, etc.)
│   ├── base.css           # Base styles and typography
│   ├── components.css     # Component-specific styles
│   └── animations.css     # Keyframe animations
├── js/
│   ├── main.js            # Main JavaScript (navigation, FAQ, etc.)
│   ├── three-scene.js     # Three.js particle system
│   └── scroll-animations.js # GSAP scroll animations
└── README.md              # This file
```

## Technologies Used

- **HTML5**: Semantic markup
- **CSS3**: Modern CSS with custom properties, Grid, and Flexbox
- **JavaScript (ES6+)**: Modern JavaScript features
- **Three.js**: 3D particle system for hero section
- **GSAP**: Professional-grade animation library with ScrollTrigger
- **Google Fonts**: Inter font family

## Setup for GitHub Pages

1. Push the `docs` folder to your GitHub repository
2. Go to repository Settings → Pages
3. Set Source to "Deploy from a branch"
4. Select branch: `main` (or your default branch)
5. Select folder: `/docs`
6. Click Save

Your site will be available at: `https://YOUR_USERNAME.github.io/Nexus/`

## Customization

### Update GitHub Links

Replace `YOUR_USERNAME` in the download buttons (line ~XXX in index.html):
```html
<a href="https://github.com/YOUR_USERNAME/Nexus/releases" target="_blank" class="btn btn-primary btn-download">
```

### Modify Colors

Edit `css/variables.css` to change the color scheme:
```css
--color-gold: #d4af37;
--color-gold-light: #f4d03f;
--color-gold-dark: #b8941f;
```

### Adjust Animations

Modify `js/scroll-animations.js` to customize scroll animation timings and effects.

### Change Particle Count

Edit `js/three-scene.js` line 23 to adjust particle density:
```javascript
const particleCount = 1000; // Increase or decrease as needed
```

## Performance

- Lightweight: Total page size < 500KB (excluding external libraries)
- Optimized animations: Uses GPU-accelerated transforms
- Lazy loading: Deferred JavaScript loading
- Responsive images: Optimized for all screen sizes

## Browser Support

- Chrome/Edge: Full support
- Firefox: Full support
- Safari: Full support
- Mobile browsers: Full support

## License

Part of the Nexus project. See main repository for license information.
