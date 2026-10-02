import ReactMarkdown from 'react-markdown';
import type { Components } from 'react-markdown';
import rehypeSanitize from 'rehype-sanitize';
import '../../styles/components/content.css';

const isExternal = (href?: string): boolean =>
  Boolean(href) && /^(https?:)?\/\//i.test(href as string) && !(href as string).startsWith(window.location.origin);

/**
 * Chỉ truyền các thuộc tính cần thiết xuống thẻ DOM (không spread props) để không lọt `node`
 * của react-markdown ra HTML.
 */
const COMPONENTS: Components = {
  a: ({ href, title, children }) =>
    isExternal(href) ? (
      <a href={href} title={title} target="_blank" rel="noopener noreferrer">
        {children}
      </a>
    ) : (
      <a href={href} title={title}>
        {children}
      </a>
    ),
  img: ({ src, alt, title }) => <img src={typeof src === 'string' ? src : undefined} alt={alt ?? ''} title={title} loading="lazy" />,
};

export interface MarkdownViewProps {
  source: string;
}

/**
 * Hiển thị Markdown an toàn (spec D1): không bật HTML thô, rehype-sanitize lọc thuộc tính/URL nguy hiểm,
 * link ra ngoài mở tab mới với rel="noopener noreferrer". Dùng chung cho tin tức và tuyển dụng.
 */
export const MarkdownView = ({ source }: MarkdownViewProps) => (
  <div className="md-view">
    <ReactMarkdown rehypePlugins={[rehypeSanitize]} components={COMPONENTS}>
      {source}
    </ReactMarkdown>
  </div>
);
