package com.beamng.remotecontrol.input;

/**
 * Tüm kontrol tiplerinin uygulaması gereken arayüz.
 * Bu sayede farklı kontrol yöntemleri (Jiroskop, Buton, Slider) 
 * kolayca değiştirilebilir.
 */
public interface SteeringInputHandler {

    /**
     * Mevcut direksiyon açısını döndürür.
     * @return -1.0f (tam sol) ile 1.0f (tam sağ) arasında değer. 0 = düz.
     */
    float getSteeringValue();
    
    /**
     * Input handler'ı başlatır. Sensör dinleyicileri vb. burada kayıt edilir.
     */
    void start();
    
    /**
     * Input handler'ı durdurur. Kaynakları serbest bırakır.
     */
    void stop();
    
    /**
     * Bu handler'ın görsel UI gerektirip gerektirmediğini belirtir.
     * Örneğin: Buton ve Slider için true, Jiroskop için false.
     */
    boolean requiresUIControls();
}
