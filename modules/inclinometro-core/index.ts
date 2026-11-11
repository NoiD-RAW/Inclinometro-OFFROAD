// Re-export the native module. On web, it will be resolved to InclinometroCoreModule.web.ts
// and on native platforms to InclinometroCoreModule.ts
export { default } from './src/InclinometroCoreModule';
export * from './src/InclinometroCore.types';
