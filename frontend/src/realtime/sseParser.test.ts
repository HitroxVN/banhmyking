import { describe, expect, it } from 'vitest';
import { createSseParser } from './sseParser';

describe('createSseParser', () => {
  it('đọc một tin đầy đủ theo định dạng Spring (không có dấu cách sau dấu hai chấm)', () => {
    const parse = createSseParser();
    expect(parse('event:order\ndata:{"orderCode":"A"}\n\n')).toEqual([{ event: 'order', data: '{"orderCode":"A"}' }]);
  });

  it('ghép tin bị cắt giữa hai khúc', () => {
    const parse = createSseParser();
    expect(parse('event:ord')).toEqual([]);
    expect(parse('er\ndata:{"a":1}')).toEqual([]);
    expect(parse('\n\n')).toEqual([{ event: 'order', data: '{"a":1}' }]);
  });

  it('tách nhiều tin trong một khúc', () => {
    const parse = createSseParser();
    expect(parse('event:ready\ndata:{}\n\nevent:inbox\ndata:{"type":"FEEDBACK"}\n\n')).toEqual([
      { event: 'ready', data: '{}' },
      { event: 'inbox', data: '{"type":"FEEDBACK"}' },
    ]);
  });

  it('bỏ qua dòng chú thích ping', () => {
    const parse = createSseParser();
    expect(parse(':ping\n\n')).toEqual([]);
  });

  it('hiểu \\r\\n, kể cả khi \\r và \\n rơi vào hai khúc khác nhau', () => {
    const parse = createSseParser();
    expect(parse('event: order\r\ndata: x\r')).toEqual([]);
    expect(parse('\n\r\n')).toEqual([{ event: 'order', data: 'x' }]);
  });

  it('nối nhiều dòng data bằng xuống dòng và mặc định tên tin là message', () => {
    const parse = createSseParser();
    expect(parse('data:a\ndata:b\n\n')).toEqual([{ event: 'message', data: 'a\nb' }]);
  });
});
