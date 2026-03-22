// Custom sign function that does nothing
// This prevents electron-builder from downloading winCodeSign
exports.default = async function(configuration) {
  // Do nothing - skip code signing
  console.log('Skipping code signing (unsigned build)');
};
