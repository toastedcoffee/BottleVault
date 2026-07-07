import { afterEach } from 'vitest';
import '@testing-library/jest-dom/vitest';

afterEach(() => {
  // Auth state lives in sessionStorage; never let it leak between tests.
  sessionStorage.clear();
});
