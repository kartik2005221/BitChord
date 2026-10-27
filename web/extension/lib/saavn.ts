/**
 * JioSaavn, the fork's own music source: on by default, and a toggle in the
 * popup's Sources row turns it off. The value lives in this profile's
 * storage, and the page reads it through the bridge (`saavn.enabled`) before
 * it adds the catalogue's rows to a search.
 */

/** The key the toggle lives under. */
export const SAAVN_KEY = 'saavn.enabled';

export async function saavnEnabled(): Promise<boolean> {
  const stored = await browser.storage.local.get(SAAVN_KEY);
  const value = stored[SAAVN_KEY];
  return typeof value === 'boolean' ? value : true;
}

export async function setSaavnEnabled(enabled: boolean): Promise<void> {
  await browser.storage.local.set({ [SAAVN_KEY]: enabled });
}
