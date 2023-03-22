#version 330
// -- part of the filter interface, every filter has these
in vec2 v_texCoord0;
uniform sampler2D tex0;
out vec4 o_color;

// -- user parameters
uniform float darkFac;

void main() {
   vec4 fac = vec4((1.0 - darkFac), (1.0 - darkFac), (1.0 - darkFac), 1.0);
   o_color = texture(tex0, v_texCoord0) * fac;
}