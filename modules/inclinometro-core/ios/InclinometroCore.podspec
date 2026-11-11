Pod::Spec.new do |s|
  s.name           = 'InclinometroCore'
  s.version        = '1.0.0'
  s.summary        = 'Sensores, GPS, avisos y salidas del Inclinómetro OFFROAD'
  s.description    = 'Parte nativa de iOS: inclinación, brújula, GPS, avisos, grabación de salidas e imágenes del vehículo.'
  s.author         = ''
  s.homepage       = 'https://docs.expo.dev/modules/'
  s.platforms      = {
    :ios => '16.4'
  }
  s.source         = { git: '' }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'
  s.frameworks = 'CoreMotion', 'CoreLocation', 'AVFoundation', 'AudioToolbox', 'PhotosUI'
  s.library = 'sqlite3'

  # Swift/Objective-C compatibility
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
  }

  s.source_files = "**/*.{h,m,mm,swift,hpp,cpp}"
end
