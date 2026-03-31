const COUNTRY_LENGTHS: Record<string, number> = {
  RO: 24,
  DE: 22,
  FR: 27,
  GB: 22,
  IT: 27,
  ES: 24,
  NL: 18,
  BE: 16,
  AT: 20,
  PT: 25,
};

export function validateIban(iban: string): { valid: boolean; error?: string } {
  const cleaned = iban.replace(/\s/g, '').toUpperCase();

  if (cleaned.length < 15) {
    return { valid: false, error: 'IBAN is too short (minimum 15 characters)' };
  }

  if (cleaned.length > 34) {
    return { valid: false, error: 'IBAN is too long (maximum 34 characters)' };
  }

  const countryCode = cleaned.substring(0, 2);
  const expectedLength = COUNTRY_LENGTHS[countryCode];

  if (expectedLength !== undefined && cleaned.length !== expectedLength) {
    return {
      valid: false,
      error: `${countryCode} IBAN must be ${expectedLength} characters (got ${cleaned.length})`,
    };
  }

  // Move first 4 chars to end
  const rearranged = cleaned.substring(4) + cleaned.substring(0, 4);

  // Convert letters to numbers (A=10, B=11, ..., Z=35)
  let numericString = '';
  for (const char of rearranged) {
    const code = char.charCodeAt(0);
    if (code >= 65 && code <= 90) {
      numericString += (code - 55).toString();
    } else {
      numericString += char;
    }
  }

  // Mod 97 check using chunked approach for large numbers
  let remainder = 0;
  for (let i = 0; i < numericString.length; i++) {
    remainder = (remainder * 10 + parseInt(numericString[i], 10)) % 97;
  }

  if (remainder !== 1) {
    return { valid: false, error: 'Invalid IBAN checksum' };
  }

  return { valid: true };
}

export function formatIban(iban: string): string {
  const cleaned = iban.replace(/\s/g, '').toUpperCase();
  return cleaned.replace(/(.{4})/g, '$1 ').trim();
}
