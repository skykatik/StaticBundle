import com.github.skykatik.t9n.gen.AnnotationProcessor;

import javax.annotation.processing.AbstractProcessor;

module staticbundle.processor {
    requires java.compiler;

    exports com.github.skykatik.t9n.annotation;

    provides AbstractProcessor with AnnotationProcessor;
}
