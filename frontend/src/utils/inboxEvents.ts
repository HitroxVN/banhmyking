/** Phát khi hồ sơ / phản hồi vừa được xử lý để huy hiệu số NEW trên menu tải lại ngay. */
export const INBOX_CHANGED_EVENT = 'bmk:inbox-changed';

export const notifyInboxChanged = (): void => {
  window.dispatchEvent(new Event(INBOX_CHANGED_EVENT));
};
