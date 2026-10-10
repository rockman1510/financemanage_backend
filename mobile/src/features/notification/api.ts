import { http } from '@/shared/api/http';
import type { AppNotification } from '@/shared/api/types';

export const notificationApi = {
  list: () => http.get<AppNotification[]>('/api/v1/notifications'),
  unreadCount: () => http.get<{ count: number }>('/api/v1/notifications/unread-count'),
  markRead: (id: string) => http.post<void>(`/api/v1/notifications/${id}/read`),
};
