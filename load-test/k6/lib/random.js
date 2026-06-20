export function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const value = Math.floor(Math.random() * 16);
    const next = char === 'x' ? value : (value & 0x3) | 0x8;
    return next.toString(16);
  });
}

export function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

export function pickOne(items) {
  return items[randomInt(0, items.length - 1)];
}

export function shuffle(items) {
  const copy = [...items];
  for (let index = copy.length - 1; index > 0; index -= 1) {
    const target = randomInt(0, index);
    [copy[index], copy[target]] = [copy[target], copy[index]];
  }
  return copy;
}

export function weightedResult(successPercent, failurePercent) {
  const value = randomInt(1, 100);
  if (value <= failurePercent) {
    return 'FAILED';
  }
  if (value <= failurePercent + successPercent) {
    return 'SUCCESS';
  }
  return 'ABANDON';
}
