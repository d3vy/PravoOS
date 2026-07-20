import type { NavTabItem } from '../ui/NavTabs'
import {
  BillingIcon,
  CalendarIcon,
  CasesIcon,
  ChatIcon,
  ClientsIcon,
  DashboardIcon,
  InvoiceIcon,
  ProfileIcon,
  SettingsIcon,
  TeamIcon,
  TemplatesIcon,
  WorkflowsIcon,
} from './navIcons'

export interface LawyerNavSection {
  title: string
  items: NavTabItem[]
}

export const lawyerNavSections: LawyerNavSection[] = [
  {
    title: 'Главное',
    items: [
      { to: '/dashboard', label: 'Дашборд', icon: <DashboardIcon /> },
      { to: '/chat', label: 'AI-чат', icon: <ChatIcon /> },
      { to: '/cases', label: 'Дела', icon: <CasesIcon /> },
      { to: '/clients', label: 'Клиенты', icon: <ClientsIcon /> },
      { to: '/calendar', label: 'Календарь', icon: <CalendarIcon /> },
      { to: '/invoices', label: 'Счета', icon: <InvoiceIcon /> },
    ],
  },
  {
    title: 'Инструменты',
    items: [
      { to: '/templates', label: 'Шаблоны', icon: <TemplatesIcon /> },
      { to: '/workflows', label: 'Процессы', icon: <WorkflowsIcon /> },
    ],
  },
]

export const lawyerAccountLinks: NavTabItem[] = [
  { to: '/profile', label: 'Профиль', icon: <ProfileIcon /> },
  { to: '/team', label: 'Организация', icon: <TeamIcon /> },
  { to: '/billing', label: 'Подписка', icon: <BillingIcon /> },
  { to: '/settings', label: 'Настройки', icon: <SettingsIcon /> },
]

export const LAWYER_NAV_INDICATOR_ID = 'lawyer-nav-indicator'
