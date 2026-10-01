EREN - fonts folder
===================

Drop these .ttf files here (exact names). Eren looks for them at runtime:

  Oswald-Medium.ttf        -> brand font (MODMASE headline)
  Audiowide-Regular.ttf    -> UPDATE small title + button labels
  Poppins-SemiBold.ttf     -> feature lines

Google Fonts (free):
  https://fonts.google.com/specimen/Oswald
  https://fonts.google.com/specimen/Audiowide
  https://fonts.google.com/specimen/Poppins

If a file is missing, Eren silently falls back to a system font, so the APK
still builds and runs. You can also point each font at a different asset path
from the admin panel (Fonts section) - for example fonts/Inter-Bold.ttf.
