export interface SseMessage {
  event: string;
  data: string;
}

const parseBlock = (block: string): SseMessage | null => {
  let event = 'message';
  const data: string[] = [];
  for (const line of block.split('\n')) {
    if (line === '' || line.startsWith(':')) continue;
    const colon = line.indexOf(':');
    const field = colon === -1 ? line : line.slice(0, colon);
    let value = colon === -1 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    if (field === 'event') event = value;
    else if (field === 'data') data.push(value);
  }
  return data.length === 0 ? null : { event, data: data.join('\n') };
};

/**
 * Bộ đọc SSE tăng dần: nhận từng khúc văn bản từ luồng fetch (có thể cắt giữa chừng)
 * và trả các tin đã nhận đủ. Dòng chú thích (`: ping`) bị bỏ qua.
 */
export const createSseParser = () => {
  let buffer = '';

  return (chunk: string): SseMessage[] => {
    buffer += chunk;
    // '\r' ở cuối khúc có thể là nửa đầu của '\r\n' — giữ lại chờ khúc sau
    const heldBack = buffer.endsWith('\r') ? '\r' : '';
    let text = (heldBack ? buffer.slice(0, -1) : buffer).replace(/\r\n?/g, '\n');

    const messages: SseMessage[] = [];
    let boundary = text.indexOf('\n\n');
    while (boundary !== -1) {
      const message = parseBlock(text.slice(0, boundary));
      if (message) messages.push(message);
      text = text.slice(boundary + 2);
      boundary = text.indexOf('\n\n');
    }

    buffer = text + heldBack;
    return messages;
  };
};
