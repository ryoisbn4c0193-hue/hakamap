type FileSelectionActivityListener = (active: boolean) => void;

const listeners = new Set<FileSelectionActivityListener>();
let activeRequests = 0;

export function subscribeFileSelectionActivity(listener: FileSelectionActivityListener) {
  listeners.add(listener);
  listener(activeRequests > 0);
  return () => {
    listeners.delete(listener);
  };
}

export async function withFileSelectionActivity<T>(operation: () => Promise<T>): Promise<T> {
  activeRequests += 1;
  listeners.forEach((listener) => listener(true));
  try {
    return await operation();
  } finally {
    activeRequests -= 1;
    if (activeRequests === 0) {
      listeners.forEach((listener) => listener(false));
    }
  }
}
