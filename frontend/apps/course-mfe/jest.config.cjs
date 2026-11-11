/** Jest + React Testing Library. TS는 ts-jest로 변환한다 (CommonJS로 컴파일해 jest.mock이 동작하게). */
module.exports = {
  testEnvironment: 'jsdom',
  setupFilesAfterEnv: ['<rootDir>/jest.setup.ts'],
  transform: {
    '^.+\\.tsx?$': [
      'ts-jest',
      {
        tsconfig: {
          jsx: 'react-jsx',
          module: 'CommonJS',
          moduleResolution: 'Node',
          esModuleInterop: true,
          strict: true,
          skipLibCheck: true,
          types: ['jest', '@testing-library/jest-dom'],
        },
      },
    ],
  },
};
