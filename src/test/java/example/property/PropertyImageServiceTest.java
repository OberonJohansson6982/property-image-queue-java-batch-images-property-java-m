package example.property;

public final class PropertyImageServiceTest {
    public static void main(String[] args) {
        PropertyImageService.InfraiClient client = new PropertyImageService.InfraiClient("https://example.test", "course-key", null) {
            @Override String resize(String image, int width, int height) {
                if (!image.equals("scan.png") || width != 1800 || height != 2400) throw new AssertionError("document sizing decision changed");
                return "resized-scan.jpg";
            }
            @Override String publish(String queue, String payload) {
                if (!queue.equals("property-image-work") || !payload.contains("tenant-document")) throw new AssertionError("queue payload lost its domain kind");
                return "message-1";
            }
        };
        String id = new PropertyImageService(client).prepare("building-a", "scan.png", "tenant-document");
        if (!id.equals("message-1")) throw new AssertionError("unexpected queue result");
        System.out.println("property image decision test passed");
    }
}
