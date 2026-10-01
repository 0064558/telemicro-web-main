import { InjectionToken } from '@angular/core';

// Configure the public API URL here when publishing the demo.
export const API_URL = new InjectionToken<string>('API_URL', { providedIn: 'root', factory: () => '/api/v1' });
export interface ServiceOption { code: string; name: string }
export type Status = 'NEW' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
export const STATUS_LABELS: Record<Status, string> = { NEW: 'Novo', IN_PROGRESS: 'Em atendimento', COMPLETED: 'Concluído', CANCELLED: 'Cancelado' };
export interface Budget { id: string; protocol: string; customerName: string; phone: string; serviceCode: string; serviceName: string; message: string; status: Status; version: number; createdAt: string; updatedAt: string }
export interface BudgetPage { content: Budget[]; page: number; totalPages: number; totalElements: number }
export interface BudgetDetails { budget: Budget; history: { id: string; previousStatus: Status | null; newStatus: Status; changedByEmail: string | null; createdAt: string }[]; notes: { id: string; authorEmail: string; text: string; createdAt: string }[] }
export function apiError(error: unknown): string {
  const e = error as { status?: number; error?: { detail?: string; errors?: Record<string, string> } };
  if (e.status === 429) return 'Muitas tentativas. Aguarde alguns minutos e tente novamente.';
  if (e.status === 403) return 'Sua conta não tem permissão para essa ação.';
  if (e.status === 409) return 'O orçamento foi alterado. Os dados serão atualizados; confira antes de tentar novamente.';
  if (e.status === 400) return Object.values(e.error?.errors ?? {}).join(' ') || e.error?.detail || 'Confira os campos informados.';
  return 'Não foi possível concluir a operação. Tente novamente.';
}
