package com.eduvision.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A named, tappable hotspot on an {@link ArContent} model.
 * Drives the "tap a part -> hear/see its explanation" AR interaction.
 */
@Entity
@Table(name = "content_parts")
public class ContentPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    public ArContent content;

    /** Machine-facing part key, e.g. "left_ventricle". */
    @Column(name = "part_name", nullable = false, length = 120)
    public String partName;

    /** Student-facing label, e.g. "Left Ventricle". */
    @Column(name = "label", nullable = false, length = 160)
    public String label;

    @Column(name = "explanation", length = 4000)
    public String explanation;

    @Column(name = "audio_url", length = 600)
    public String audioUrl;

    @Column(name = "order_index", nullable = false)
    public int orderIndex;

    public ContentPart() {
    }

    public ContentPart(ArContent content, String partName, String label,
                       String explanation, int orderIndex) {
        this.content = content;
        this.partName = partName;
        this.label = label;
        this.explanation = explanation;
        this.orderIndex = orderIndex;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ArContent getContent() {
        return content;
    }

    public void setContent(ArContent content) {
        this.content = content;
    }

    public String getPartName() {
        return partName;
    }

    public void setPartName(String partName) {
        this.partName = partName;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }
}