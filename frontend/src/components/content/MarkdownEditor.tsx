import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent, ReactNode } from 'react';
import { Bold, Heading2, Heading3, ImagePlus, Italic, Link as LinkIcon, List } from 'lucide-react';
import { Tabs, useToast } from '../ui';
import { MarkdownView } from './MarkdownView';
import '../../styles/components/content.css';

const MAX_IMAGE_MB = 5;

type EditorTab = 'write' | 'preview';
type ToolId = 'bold' | 'italic' | 'h2' | 'h3' | 'list' | 'link';

const TOOLS: { id: ToolId; label: string; icon: ReactNode }[] = [
  { id: 'bold', label: 'In đậm', icon: <Bold size={16} /> },
  { id: 'italic', label: 'In nghiêng', icon: <Italic size={16} /> },
  { id: 'h2', label: 'Tiêu đề lớn (H2)', icon: <Heading2 size={16} /> },
  { id: 'h3', label: 'Tiêu đề nhỏ (H3)', icon: <Heading3 size={16} /> },
  { id: 'list', label: 'Danh sách', icon: <List size={16} /> },
  { id: 'link', label: 'Chèn link', icon: <LinkIcon size={16} /> },
];

export interface MarkdownEditorProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  /** Tải ảnh lên và trả về URL công khai — có thì hiện nút "Chèn ảnh" */
  onUploadImage?: (file: File) => Promise<string>;
  rows?: number;
  placeholder?: string;
}

/** Trình soạn Markdown đơn giản (spec D1): thanh công cụ chèn cú pháp + tab Xem trước. */
export const MarkdownEditor = ({ label, value, onChange, onUploadImage, rows = 14, placeholder }: MarkdownEditorProps) => {
  const toast = useToast();
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const [tab, setTab] = useState<EditorTab>('write');
  const [isUploading, setIsUploading] = useState(false);
  // Giá trị/onChange mới nhất: sau `await` tải ảnh, closure cũ sẽ ghi đè phần đã gõ trong lúc chờ
  const valueRef = useRef(value);
  const onChangeRef = useRef(onChange);
  useEffect(() => {
    valueRef.current = value;
    onChangeRef.current = onChange;
  });

  /** Thay vùng đang chọn bằng `text`, đặt con trỏ sau đoạn chèn */
  const replaceSelection = (build: (selected: string, start: number, current: string) => string) => {
    const el = textareaRef.current;
    const current = valueRef.current;
    const start = el ? Math.min(el.selectionStart, current.length) : current.length;
    const end = el ? Math.min(el.selectionEnd, current.length) : current.length;
    const text = build(current.slice(start, end), start, current);
    const next = current.slice(0, start) + text + current.slice(end);
    valueRef.current = next;
    onChangeRef.current(next);
    window.requestAnimationFrame(() => {
      if (!el) return;
      el.focus();
      const caret = start + text.length;
      el.setSelectionRange(caret, caret);
    });
  };

  const wrap = (before: string, after: string, placeholderText: string) =>
    replaceSelection((selected) => `${before}${selected || placeholderText}${after}`);

  const prefixLines = (prefix: string, placeholderText: string) =>
    replaceSelection((selected, start, current) => {
      const lead = start > 0 && current[start - 1] !== '\n' ? '\n' : '';
      const lines = (selected || placeholderText).split('\n').map((line) => `${prefix}${line}`);
      return `${lead}${lines.join('\n')}`;
    });

  const handleImage = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file || !onUploadImage) return;
    if (!file.type.startsWith('image/')) {
      toast.error('Tệp chèn phải là ảnh (PNG, JPG, WEBP, GIF).');
      return;
    }
    if (file.size > MAX_IMAGE_MB * 1024 * 1024) {
      toast.error(`Dung lượng ảnh không được vượt quá ${MAX_IMAGE_MB}MB.`);
      return;
    }
    setIsUploading(true);
    try {
      const url = await onUploadImage(file);
      replaceSelection((_selected, start, current) => `${start > 0 && current[start - 1] !== '\n' ? '\n' : ''}![Mô tả ảnh](${url})\n`);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Tải ảnh lên thất bại');
    } finally {
      setIsUploading(false);
    }
  };

  const runTool = (id: ToolId): void => {
    switch (id) {
      case 'bold': return wrap('**', '**', 'chữ đậm');
      case 'italic': return wrap('_', '_', 'chữ nghiêng');
      case 'h2': return prefixLines('## ', 'Tiêu đề');
      case 'h3': return prefixLines('### ', 'Tiêu đề nhỏ');
      case 'list': return prefixLines('- ', 'Mục');
      case 'link': return wrap('[', '](https://)', 'chữ hiển thị');
    }
  };


  return (
    <div className="md-editor">
      <span className="ui-field__label">{label}</span>
      <Tabs<EditorTab>
        tabs={[
          { key: 'write', label: 'Soạn' },
          { key: 'preview', label: 'Xem trước' },
        ]}
        value={tab}
        onChange={setTab}
      />

      {tab === 'write' ? (
        <>
          <div className="md-editor__toolbar" role="toolbar" aria-label="Định dạng Markdown">
            {TOOLS.map((tool) => (
              <button key={tool.label} type="button" className="md-editor__btn" title={tool.label} aria-label={tool.label} onClick={() => runTool(tool.id)}>
                {tool.icon}
              </button>
            ))}
            {onUploadImage && (
              <>
                <button
                  type="button"
                  className="md-editor__btn"
                  title="Chèn ảnh"
                  aria-label="Chèn ảnh"
                  disabled={isUploading}
                  onClick={() => fileRef.current?.click()}
                >
                  <ImagePlus size={16} />
                </button>
                <input ref={fileRef} type="file" accept="image/*" hidden onChange={(event) => void handleImage(event)} />
              </>
            )}
            {isUploading && <span className="md-editor__hint">Đang tải ảnh…</span>}
          </div>
          <textarea
            ref={textareaRef}
            className="ui-field__input md-editor__area"
            rows={rows}
            value={value}
            placeholder={placeholder}
            onChange={(event) => onChange(event.target.value)}
          />
          <span className="md-editor__hint">
            Hỗ trợ **đậm**, _nghiêng_, ## tiêu đề, - danh sách, [link](https://…), ![ảnh](url). Không chèn được HTML.
          </span>
        </>
      ) : (
        <div className="md-editor__preview">
          {value.trim() ? <MarkdownView source={value} /> : <p className="md-editor__hint">Chưa có nội dung để xem trước.</p>}
        </div>
      )}
    </div>
  );
};
