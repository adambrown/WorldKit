#version 330

uniform mat4 modelViewProjectionMatrix;
uniform mat4 modelViewMatrix;
uniform mat3 normalMatrix;
uniform vec3 lightDirection;
uniform vec4 color;
uniform vec4 ambientColor;
uniform vec4 diffuseColor;
uniform vec4 specularColor;
uniform float shininess;
uniform float heightScale;
uniform float uvScale;
uniform sampler2D heightMapTexture;


in VertexData {
    vec2 position;
    vec2 uv;
} VertexIn;

layout(location = 0) out vec4 colorOut;

const ivec3 off = ivec3(-1, 0, 1);

const float gamma = 2.2;

vec3 toLinear(vec3 v) {
  return pow(v, vec3(gamma));
}

vec4 toLinear(vec4 v) {
  return vec4(toLinear(v.rgb), v.a);
}

vec3 toGamma(vec3 v) {
  return pow(v, vec3(1.0 / gamma));
}

vec4 toGamma(vec4 v) {
  return vec4(toGamma(v.rgb), v.a);
}

void main() {
    vec2 size = vec2(uvScale * 2, 0.0);
    float height = texture(heightMapTexture, VertexIn.uv).r * heightScale;
    float westPixel = textureOffset(heightMapTexture, VertexIn.uv, off.xy).r * heightScale;
    float eastPixel = textureOffset(heightMapTexture, VertexIn.uv, off.zy).r * heightScale;
    float northPixel = textureOffset(heightMapTexture, VertexIn.uv, off.yx).r * heightScale;
    float southPixel = textureOffset(heightMapTexture, VertexIn.uv, off.yz).r * heightScale;
    float northWestPixel = textureOffset(heightMapTexture, VertexIn.uv, off.xx).r * heightScale;
    float southEastPixel = textureOffset(heightMapTexture, VertexIn.uv, off.zz).r * heightScale;
    float northEastPixel = textureOffset(heightMapTexture, VertexIn.uv, off.zx).r * heightScale;
    float southWestPixel = textureOffset(heightMapTexture, VertexIn.uv, off.xz).r * heightScale;
    vec3 va = normalize(vec3(size.xy, eastPixel - westPixel));
    vec3 vb = normalize(vec3(size.yx, southPixel - northPixel));
    vec3 vc = normalize(vec3(size.xx, southEastPixel - northWestPixel));
    vec3 vd = normalize(vec3(size.x, -size.x, southWestPixel - northEastPixel));
    vec3 crossNormal = cross(va, vb);
    crossNormal = normalize(vec3(crossNormal.x, -crossNormal.y, crossNormal.z));
    vec3 xNormal = cross(vd, vc);
    xNormal = normalize(vec3(xNormal.y, -xNormal.x, xNormal.z));


    vec3 modelNormal = normalize(xNormal + crossNormal);
    vec3 normal = normalize(normalMatrix * modelNormal);
    float diffuse;
    if (gl_FrontFacing) {
        diffuse = max(dot(normal, lightDirection), 0.0) + 0.06;
    } else {
        diffuse = 0.0;
    }
    vec4 baseColor = toLinear(color);
    vec4 lightColor = toLinear(vec4(1.0));
    vec4 diffuseColor = vec4(baseColor.rgb * lightColor.rgb * diffuse, 1.0);
    vec4 spec = vec4(0.0);
    vec3 eye = -normalize(vec3(modelViewMatrix * vec4(VertexIn.position.xy, height, 1.0)));
    float intensity = max(dot(normal, lightDirection), 0.0);
    if (intensity > 0.0) {
        vec3 halfVector = normalize(lightDirection + eye);
        spec = toLinear(specularColor) * pow(max(dot(halfVector, normal), 0.0), shininess);
    }
    colorOut = toGamma(diffuseColor * max(intensity * toLinear(diffuseColor) + spec, toLinear(ambientColor)));
}
