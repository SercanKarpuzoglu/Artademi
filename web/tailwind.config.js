/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      // design-reference.html paleti (erik + ahududu). Hex bileşene gömülmez; token kullanılır.
      colors: {
        ink: { DEFAULT: '#2a2230', soft: '#5d5366' },
        paper: '#f5f4f7',
        card: '#ffffff',
        line: '#e7e3ec',
        rasp: { DEFAULT: '#b43a5e', soft: '#f7e6ec' },
        plum: '#4a3b5c',
        green: { DEFAULT: '#2f7d5b', soft: '#e3f1ea' },
        red: { DEFAULT: '#b23a3a', soft: '#f7e6e6' },
        amber: { DEFAULT: '#b9791e', soft: '#f8eedb' },
        blue: { DEFAULT: '#3a5e9c', soft: '#e5ecf7' },
        'gray-soft': '#eeebf2',
      },
      fontFamily: {
        fraunces: ['Fraunces', 'serif'],
        manrope: ['Manrope', 'sans-serif'],
      },
      boxShadow: {
        card: '0 1px 2px rgba(42,34,48,.05), 0 6px 18px rgba(42,34,48,.06)',
      },
      borderRadius: {
        card: '14px',
      },
    },
  },
  plugins: [],
};
