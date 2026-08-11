import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { useAuthStore } from '../../store/authStore'
import { PravoIcon } from '../ui/Logo'

const NAV_LINKS = [
  { href: '#product', key: 'landing.navProduct' },
  { href: '#early-access', key: 'landing.navEarlyAccess' },
  { href: '#contacts', key: 'landing.navContacts' },
]

function homePathForRole(role: string | undefined): string {
  if (role === 'ADMIN') return '/admin/applications'
  if (role === 'CLIENT') return '/portal'
  return '/dashboard'
}

export function LandingHeader(): JSX.Element {
  const { t } = useTranslation()
  const { isAuthenticated, effectiveRole } = useAuthStore()
  const [menuOpen, setMenuOpen] = useState(false)
  const authenticated = isAuthenticated()

  return (
    <header className="relative z-30">
      <div className="page-container">
        <nav className="flex items-center justify-between h-16 sm:h-20">
          <Link to="/" className="flex items-center gap-2 shrink-0">
            <PravoIcon className="w-8 h-8 rounded-md" />
            <span className="font-display text-lg text-[#1A0F0A]">
              Pravo<span className="opacity-60">OS</span>
            </span>
          </Link>

          <div className="hidden md:flex items-center gap-8">
            {NAV_LINKS.map((link) => (
              <a
                key={link.href}
                href={link.href}
                className="text-[15px] text-[#1A0F0A] hover:opacity-70 transition-opacity"
              >
                {t(link.key)}
              </a>
            ))}
          </div>

          <div className="hidden sm:flex items-center gap-3">
            {authenticated ? (
              <Link
                to={homePathForRole(effectiveRole())}
                className="inline-flex items-center justify-center px-5 py-2.5 rounded-full bg-[#1A0F0A] text-white text-[15px] hover:opacity-90 transition-opacity"
              >
                {t('landing.navWorkspace')}
              </Link>
            ) : (
              <>
                <Link
                  to="/login"
                  className="inline-flex items-center justify-center px-5 py-2.5 rounded-full border border-[#1A0F0A]/20 text-[#1A0F0A] text-[15px] hover:bg-[#1A0F0A]/5 transition-colors"
                >
                  {t('landing.navLogin')}
                </Link>
                <Link
                  to="/apply"
                  className="inline-flex items-center justify-center px-5 py-2.5 rounded-full bg-[#1A0F0A] text-white text-[15px] hover:opacity-90 transition-opacity"
                >
                  {t('landing.navApply')}
                </Link>
              </>
            )}
          </div>

          <button
            type="button"
            aria-label={t('nav.menu')}
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((open) => !open)}
            className="md:hidden flex flex-col items-end justify-center gap-1.5 w-10 h-10"
          >
            <span className={`h-px bg-[#1A0F0A] transition-all ${menuOpen ? 'w-5 -rotate-45 translate-y-[3px]' : 'w-5'}`} />
            <span className={`h-px bg-[#1A0F0A] transition-all ${menuOpen ? 'w-5 rotate-45 -translate-y-[3px]' : 'w-4'}`} />
          </button>
        </nav>
      </div>

      {menuOpen && (
        <div className="md:hidden page-container pb-6 flex flex-col gap-4">
          {NAV_LINKS.map((link) => (
            <a
              key={link.href}
              href={link.href}
              onClick={() => setMenuOpen(false)}
              className="text-[15px] text-[#1A0F0A]"
            >
              {t(link.key)}
            </a>
          ))}
          <div className="flex items-center gap-3 pt-2">
            {authenticated ? (
              <Link
                to={homePathForRole(effectiveRole())}
                className="inline-flex items-center justify-center px-5 py-2.5 rounded-full bg-[#1A0F0A] text-white text-[15px]"
              >
                {t('landing.navWorkspace')}
              </Link>
            ) : (
              <>
                <Link to="/login" className="inline-flex items-center justify-center px-5 py-2.5 rounded-full border border-[#1A0F0A]/20 text-[#1A0F0A] text-[15px]">
                  {t('landing.navLogin')}
                </Link>
                <Link to="/apply" className="inline-flex items-center justify-center px-5 py-2.5 rounded-full bg-[#1A0F0A] text-white text-[15px]">
                  {t('landing.navApply')}
                </Link>
              </>
            )}
          </div>
        </div>
      )}
    </header>
  )
}
