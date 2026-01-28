import React from "react";

export function useLocalStorageState<T>(
  key: string,
  defaultValue: T | (() => T)
): readonly [T, React.Dispatch<React.SetStateAction<T>>] {
  const skipWriteRef = React.useRef(false);
  const lastKeyRef = React.useRef(key);

  const resolveDefault = React.useCallback(() => {
    return defaultValue instanceof Function ? defaultValue() : defaultValue;
  }, [defaultValue]);

  const [state, setState] = React.useState<T>(() => {
    if (typeof window === "undefined") {
      return resolveDefault();
    }

    try {
      const raw = window.localStorage.getItem(key);
      if (raw === null) {
        return resolveDefault();
      }
      return JSON.parse(raw) as T;
    } catch {
      return resolveDefault();
    }
  });

  React.useEffect(() => {
    if (lastKeyRef.current === key) {
      return;
    }
    lastKeyRef.current = key;
    skipWriteRef.current = true;

    if (typeof window === "undefined") {
      setState(resolveDefault());
      return;
    }

    try {
      const raw = window.localStorage.getItem(key);
      if (raw === null) {
        setState(resolveDefault());
      } else {
        setState(JSON.parse(raw) as T);
      }
    } catch {
      setState(resolveDefault());
    }
  }, [key, resolveDefault]);

  React.useEffect(() => {
    if (skipWriteRef.current) {
      skipWriteRef.current = false;
      return;
    }

    try {
      window.localStorage.setItem(key, JSON.stringify(state));
    } catch {
      // Ignore write failures (private mode / disabled storage).
    }
  }, [key, state]);

  return [state, setState] as const;
}
