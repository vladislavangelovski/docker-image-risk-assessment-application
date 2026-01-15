import React from "react";

export function useLocalStorageState<T>(
  key: string,
  defaultValue: T | (() => T)
): readonly [T, React.Dispatch<React.SetStateAction<T>>] {
  const [state, setState] = React.useState<T>(() => {
    if (typeof window === "undefined") {
      return defaultValue instanceof Function ? defaultValue() : defaultValue;
    }

    try {
      const raw = window.localStorage.getItem(key);
      if (raw === null) {
        return defaultValue instanceof Function ? defaultValue() : defaultValue;
      }
      return JSON.parse(raw) as T;
    } catch {
      return defaultValue instanceof Function ? defaultValue() : defaultValue;
    }
  });

  React.useEffect(() => {
    try {
      window.localStorage.setItem(key, JSON.stringify(state));
    } catch {
      // Ignore write failures (private mode / disabled storage).
    }
  }, [key, state]);

  return [state, setState] as const;
}

